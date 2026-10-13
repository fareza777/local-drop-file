package com.localdrop.app.server

import java.io.*
import java.net.*
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Short-lived bearer used by Browser Share. Transfer pairing issues additional short-lived tokens. */
class SessionToken private constructor(val value:String,val expiresAt:Instant) {
 fun isValid(candidate:String,now:Instant=Instant.now())=now.isBefore(expiresAt)&&MessageDigest.isEqual(value.toByteArray(),candidate.toByteArray())
 companion object { fun issue(ttlSeconds:Long=900):SessionToken { require(ttlSeconds in 30..3600); return SessionToken(Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also{SecureRandom().nextBytes(it)}),Instant.now().plusSeconds(ttlSeconds)) } }
}

/** Optional persistence adapter. Implement with Android SharedPreferences/DataStore in the UI layer. */
interface TrustedDeviceStore { fun put(deviceId:String,deviceName:String,fingerprint:String); fun contains(deviceId:String,fingerprint:String):Boolean }
class FileTrustedDeviceStore(private val file:File):TrustedDeviceStore {
 private val lock=Any()
 override fun put(deviceId:String,deviceName:String,fingerprint:String){ synchronized(lock){ file.parentFile?.mkdirs(); file.appendText(deviceId+"\t"+deviceName.replace('\t',' ')+"\t"+fingerprint+"\n") } }
 override fun contains(deviceId:String,fingerprint:String)=synchronized(lock){ file.takeIf{it.exists()}?.readLines()?.any{it.split('\t').let{p->p.size>=3&&p[0]==deviceId&&p[2]==fingerprint}}==true }
}

/** Plain HTTP, LAN/VPN-only API. No cloud transport is used. */
class LocalHttpServer(private val root:File,private val token:SessionToken,private val trusted:TrustedDeviceStore?=null,private val pageProvider:()->String={DEFAULT_PAGE},private val accessPin:String?=null) {
 /** Browser sessions that have entered the access PIN (only used when accessPin is set). */
 private val unlockedSessions=ConcurrentHashMap<String,Boolean>()
 companion object { const val PORT=5252; const val DEFAULT_PAGE="<!doctype html><html><body><p>LocalDrop</p></body></html>" }
 // Bounded handler pool: a grid of hundreds of image thumbnails must not spawn hundreds of threads (that is what killed the app during bulk ops).
 private val acceptPool=Executors.newSingleThreadExecutor(); private val pool=Executors.newFixedThreadPool(8); @Volatile private var socket:ServerSocket?=null; var origin=""; private set
 private val paired=ConcurrentHashMap<String,SessionToken>(); private val requests=ConcurrentHashMap<String,PairRequest>(); private val transfers=ConcurrentHashMap<String,Transfer>()
 private val random=SecureRandom(); private val maxFile=100L*1024*1024; private val maxChunk=4L*1024*1024
 data class PairRequest(val id:String,val pin:String,val fingerprint:String,val deviceId:String,val deviceName:String,val expires:Long,@Volatile var approved:Boolean=false)
 data class Transfer(val id:String,val fileId:String,val name:String,val size:Long,val expected:String,val temp:File,var received:Long=0)
 /** Called on the server thread when a new pairing request arrives. UI should post to the main thread. */
 @Volatile var onPairRequest:((PairRequest)->Unit)?=null
 /** Called when the pending list changes because a pairing finished. */
 @Volatile var onPairingChanged:(()->Unit)?=null
 /** Called whenever a transfer completes, so the app can record History. [outgoing]=true when the file left this phone. */
 @Volatile var onTransfer:((name:String,size:Long,outgoing:Boolean)->Unit)?=null
 fun start():String { if(socket!=null)return origin; root.mkdirs(); socket=try{ServerSocket(PORT,20,InetAddress.getByName("0.0.0.0"))}catch(_:Exception){ServerSocket(0,20,InetAddress.getByName("0.0.0.0"))}; val s=socket!!; origin="http://${lanAddress()}:${s.localPort}"; acceptPool.execute { while(!s.isClosed) try { val c=s.accept(); pool.execute{handle(c)} } catch(_:Exception){} }; return origin }
 fun stop(){socket?.close();socket=null;origin="";transfers.clear()}; fun isRunning()=socket?.isClosed==false
 @Volatile var lastActivity:Long=System.currentTimeMillis(); private set
 private fun handle(c:Socket){ c.use { val input=BufferedInputStream(c.getInputStream());val out=BufferedOutputStream(c.getOutputStream());val req=readHeaders(input)?:return; lastActivity=System.currentTimeMillis()
  val auth=(req.headers["authorization"]?.removePrefix("Bearer ") ?: req.query["token"] ?: "").trim()
  // Pairing starts unauthenticated, but approval happens only on the phone (see approvePairing).
  val open=req.path=="/api/v1/pair/request"||req.path=="/api/v1/pair/verify"||req.path=="/api/v1/health"||req.path=="/"||req.path=="/api/v1/unlock"
  if(!open&&!validAuth(auth)){respond(out,"Unauthorized","text/plain",401);return}
  // Typing the short PIN from the phone gives this browser a session token, so the long link is not needed.
  if(req.path=="/api/v1/unlock"){
    val pin=(req.query["pin"]?:"").trim()
    // With no PIN set, this simply hands the page a session token; with a PIN, the PIN must match.
    if(accessPin==null||pin==accessPin){ val t=SessionToken.issue(3600); paired[t.value]=t; respond(out,"{\"ok\":true,\"token\":\"${t.value}\"}","application/json") }
    else respond(out,"{\"ok\":false}","application/json",403)
    return
  }
  // With PIN protection on, the data routes need a token obtained from the PIN (or the QR link token).
  if(accessPin!=null&&!req.path.startsWith("/api/v1/unlock")&&req.path!="/"&&req.path!="/api/v1/health"&&!pairingRoute(req.path)&&!validAuth(auth)){respond(out,"PIN required","text/plain",403);return}
  when { req.method=="GET"&&req.path=="/api/v1/health"->respond(out,"{\"name\":\"LocalDrop Android\",\"device_id\":\"android-${root.name}\",\"protocol\":\"v1\"}","application/json")
   req.method=="GET"&&req.path=="/"->respond(out,pageProvider(),"text/html; charset=utf-8")
   req.method=="GET"&&req.path=="/api/v1/files"->respond(out,listFiles(),"application/json")
   req.method=="GET"&&req.path=="/api/v1/thumb"->thumbnail(out,req)
   req.method=="GET"&&req.path.startsWith("/api/v1/files/")->download(out,req.path.removePrefix("/api/v1/files/"),req.headers["sec-fetch-dest"]=="image")
   req.method=="POST"&&req.path=="/api/v1/files"->upload(out,input,req)
   req.method=="POST"&&req.path=="/api/v1/pair/request"->pairRequest(out,input,req)
   req.method=="POST"&&req.path=="/api/v1/pair/verify"->pairVerify(out,input,req)
   req.method=="GET"&&req.path=="/api/v1/transfer/files"->respond(out,listTransferFiles(req.query["path"]?:""),"application/json")
   req.method=="GET"&&req.path=="/api/v1/transfer/tree"->respond(out,folderTree(),"application/json")
   req.method=="POST"&&req.path=="/api/v1/transfer/folder"->createFolder(out,input,req)
   req.method=="DELETE"&&req.path=="/api/v1/transfer/path"->deletePath(out,req)
   (req.method=="POST"||req.method=="PUT")&&req.path=="/api/v1/transfer/chunk"->chunk(out,input,req)
   req.method=="GET"&&req.path=="/api/v1/transfer/chunk"->downloadChunk(out,req)
   req.method=="POST"&&req.path=="/api/v1/transfer/upload"->uploadToFolder(out,input,req)
   req.method=="POST"&&req.path=="/api/v1/transfer/delete-many"->deleteMany(out,input,req)
   req.method=="POST"&&req.path=="/api/v1/transfer/move"->moveMany(out,input,req)
   req.method=="POST"&&req.path=="/api/v1/transfer/rename"->renamePath(out,input,req)
   req.method=="POST"&&req.path=="/api/v1/transfer/copy"->copyMany(out,input,req)
   req.method=="GET"&&req.path=="/api/v1/transfer/info"->pathInfo(out,req)
   req.method=="POST"&&req.path=="/api/v1/transfer/zip"->zipFiles(out,input,req)
   else->respond(out,"Not found","text/plain",404) }
 } }
 private fun validAuth(a:String)=token.isValid(a)||paired.entries.any{it.value.isValid(a)}
 private fun pairingRoute(p:String)=p=="/api/v1/pair/request"||p=="/api/v1/pair/verify"
 private fun body(i:InputStream,r:Request,max:Int=64*1024):String { val n=r.headers["content-length"]?.toIntOrNull()?:return "";if(n<0||n>max)throw IOException("body too large");val b=ByteArray(n);var p=0;while(p<n){val x=i.read(b,p,n-p);if(x<0)throw EOFException();p+=x};return String(b,StandardCharsets.UTF_8) }
 /** Desktop asks to pair. Creates a request that the phone user must approve on screen. */
 private fun pairRequest(o:BufferedOutputStream,i:InputStream,r:Request){try{val j=body(i,r);val fp=field(j,"fingerprint");val device=field(j,"device_id").ifBlank{UUID.randomUUID().toString()};val name=field(j,"name").ifBlank{"Desktop $device"}.take(60);if(fp.isBlank()||fp.length>200)throw IOException();val pin=(100000+random.nextInt(900000)).toString();val id=UUID.randomUUID().toString();val req=PairRequest(id,pin,fp,device,name,System.currentTimeMillis()+5*60*1000);requests[id]=req;onPairRequest?.invoke(req);respond(o,"{\"request_id\":\"$id\",\"expires_in\":300}","application/json",201)}catch(_:Exception){respond(o,"Invalid pairing request","text/plain",400)} }
 /** Desktop submits the PIN it was shown. Succeeds only if the phone user approved this request. */
 private fun pairVerify(o:BufferedOutputStream,i:InputStream,r:Request){try{val j=body(i,r);val id=field(j,"request_id");val pin=field(j,"pin");val q=requests[id]?:throw IOException();if(!q.approved||q.expires<System.currentTimeMillis()||!MessageDigest.isEqual(pin.toByteArray(),q.pin.toByteArray()))throw IOException();requests.remove(id);trusted?.put(q.deviceId,q.deviceName,q.fingerprint);onPairingChanged?.invoke();/* Paired desktops stay logged in for 30 days so the desktop app can restore its session after a restart without asking for a new PIN. */val ttl=30L*24*3600;val t=SessionToken.issue(ttl);paired[t.value]=t;respond(o,"{\"access_token\":\"${t.value}\",\"device_id\":\"${escape(q.deviceId)}\",\"expires_in\":$ttl}","application/json")}catch(_:Exception){respond(o,"Pairing failed","text/plain",401)} }
 /** Called from the phone UI. Only a local action can approve; no HTTP route exists for it. */
 fun approvePairing(requestId:String):Boolean{val q=requests[requestId]?:return false;if(q.expires<System.currentTimeMillis())return false;q.approved=true;return true}
 fun rejectPairing(requestId:String){requests.remove(requestId)}
 /** Requests still open: waiting for a decision, or approved and waiting for the PIN to be entered. */
 fun pendingRequests():List<PairRequest> = requests.values.filter{it.expires>=System.currentTimeMillis()}
 private fun folderTree():String { val out=StringBuilder("[");var count=0;fun walk(dir:File,rel:String,depth:Int){ if(depth>6||count>5000)return; for(c in dir.listFiles().orEmpty().filter{it.isDirectory&&!it.name.startsWith(".")}.sortedBy{it.name.lowercase()}){ if(count>=5000)return; val r=if(rel.isBlank())c.name else rel+"/"+c.name; if(count>0)out.append(','); out.append("{\"id\":\"${escape(r)}\",\"name\":\"${escape(c.name)}\",\"depth\":$depth}"); count++; walk(c,r,depth+1) } }; walk(root.canonicalFile,"",1); return out.append(']').toString() }
 private fun listTransferFiles(path:String):String { val dir=safeDirectory(path)?:return "[]"; return dir.listFiles().orEmpty().filter{!it.name.startsWith(".")}.joinToString(",","["){ val rel=if(path.isBlank())it.name else path+"/"+it.name; if(it.isDirectory) "{\"file_id\":\"${escape(rel)}\",\"name\":\"${escape(it.name)}\",\"type\":\"directory\",\"size\":0,\"modified\":${it.lastModified()},\"hasSub\":${it.listFiles().orEmpty().any{c->c.isDirectory&&!c.name.startsWith(".")}}}" else "{\"file_id\":\"${escape(rel)}\",\"name\":\"${escape(it.name)}\",\"type\":\"file\",\"size\":${it.length()},\"modified\":${it.lastModified()}}" }+"]" }
private fun safeDirectory(path:String):File? { if(path.length>500)return null; if(path.isBlank())return root.canonicalFile; if(path.split('/').any{it.isBlank()||it==".."||it.contains('\\')||it.any(Char::isISOControl)})return null; val base=root.canonicalFile; return File(base,path).canonicalFile.takeIf{it.path.startsWith(base.path+File.separator)&&it.isDirectory} }
private fun safePath(path:String):File? { if(path.length>500||path.isBlank()||path.split('/').any{it.isBlank()||it==".."||it.contains('\\')||it.any(Char::isISOControl)})return null; val base=root.canonicalFile; return File(base,path).canonicalFile.takeIf{it.path.startsWith(base.path+File.separator)} }
private fun createFolder(o:BufferedOutputStream,i:InputStream,r:Request){try{val path=field(body(i,r),"path");val dir=safeDirectory(path)?:throw IOException();if(!dir.mkdirs()&&!dir.isDirectory)throw IOException();respond(o,"{\"ok\":true,\"path\":\"${escape(path)}\"}","application/json",201)}catch(_:Exception){respond(o,"Invalid folder","text/plain",400)}}
/** Bulk delete: body is {"paths":["a","b/c"]}. Each path is validated the same way as single delete. */
private fun deleteMany(o:BufferedOutputStream,i:InputStream,r:Request){try{val paths=jsonStringArray(body(i,r),"paths");var n=0;for(p in paths){val f=safePath(p)?:continue;if(f.isDirectory)f.deleteRecursively() else f.delete();n++};respond(o,"{\"ok\":true,\"deleted\":$n}","application/json")}catch(_:Exception){respond(o,"Invalid request","text/plain",400)}}
/** Bulk move: body is {"paths":[...],"dest":"folder"}. Destination must be an existing folder inside the drop root. */
// Streams a ZIP of the requested files straight to the client, reading one file at a time.
private fun zipFiles(o:BufferedOutputStream,i:InputStream,r:Request){
  try{
    val j=body(i,r); val paths=jsonStringArray(j,"paths").take(500)
    val files=paths.mapNotNull{safePath(it)}.filter{it.isFile}
    if(files.isEmpty()){respond(o,"Nothing to zip","text/plain",400);return}
    o.write("HTTP/1.1 200 OK\r\nContent-Type: application/zip\r\nContent-Disposition: attachment; filename=\"localdrop-files.zip\"\r\nConnection: close\r\n\r\n".toByteArray())
    val zip=java.util.zip.ZipOutputStream(o)
    val used=HashSet<String>()
    for(f in files){
      // Keep names unique inside the archive so two files with the same name do not collide.
      var name=f.name; var n=1
      while(!used.add(name)){ val dot=f.name.lastIndexOf('.'); name=if(dot>0) f.name.substring(0,dot)+" ($n)"+f.name.substring(dot) else f.name+" ($n)"; n++ }
      zip.putNextEntry(java.util.zip.ZipEntry(name))
      f.inputStream().use{it.copyTo(zip)}
      zip.closeEntry()
    }
    zip.finish(); o.flush()
    onTransfer?.invoke("${files.size} files (ZIP)",files.sumOf{it.length()},true)
  }catch(_:Exception){ /* the response may already be streaming; nothing else to send */ }
}
private fun moveMany(o:BufferedOutputStream,i:InputStream,r:Request){try{val j=body(i,r);val paths=jsonStringArray(j,"paths");val dest=safeDirectory(field(j,"dest"))?:throw IOException();var n=0;for(p in paths){val src=safePath(p)?:continue;val target=File(dest,src.name);if(target.exists()||src==dest||src.parentFile==dest)continue;if(!src.renameTo(target))continue;n++};respond(o,"{\"ok\":true,\"moved\":$n}","application/json")}catch(_:Exception){respond(o,"Invalid request","text/plain",400)}}
/** Rename: body {"path":"a/b.txt","name":"c.txt"}. Stays in the same folder; never overwrites. */
private fun renamePath(o:BufferedOutputStream,i:InputStream,r:Request){try{val j=body(i,r);val src=safePath(field(j,"path"))?:throw IOException();val name=field(j,"name");if(name.isBlank()||name.length>240||name.contains('/')||name.contains('\\')||name==".."||name.any{it.isISOControl()})throw IOException();val target=File(src.parentFile,name);if(target.exists())throw IOException("exists");if(!src.renameTo(target))throw IOException();respond(o,"{\"ok\":true}","application/json")}catch(_:Exception){respond(o,"Could not rename","text/plain",400)}}
/** Copy: body {"paths":[...],"dest":"folder"}. Recursively copies folders; skips names that already exist in dest. */
private fun copyMany(o:BufferedOutputStream,i:InputStream,r:Request){try{val j=body(i,r);val paths=jsonStringArray(j,"paths");val dest=safeDirectory(field(j,"dest"))?:throw IOException();var n=0;for(p in paths){val src=safePath(p)?:continue;val target=File(dest,src.name);if(target.exists()||src==dest)continue;if(src.isDirectory){if(dest.canonicalPath.startsWith(src.canonicalPath+File.separator))continue;src.copyRecursively(target,overwrite=false)}else src.copyTo(target);n++};respond(o,"{\"ok\":true,\"copied\":$n}","application/json")}catch(_:Exception){respond(o,"Could not copy","text/plain",400)}}
/** Info for one path: size, modified time, and item count for folders. */
private fun pathInfo(o:BufferedOutputStream,r:Request){try{val f=safePath(r.query["path"]?:"")?:throw IOException();val count=if(f.isDirectory)f.listFiles().orEmpty().size else 0;val size=if(f.isDirectory)f.walkTopDown().filter{it.isFile}.sumOf{it.length()} else f.length();respond(o,"{\"name\":\"${escape(f.name)}\",\"type\":\"${if(f.isDirectory)"directory" else "file"}\",\"size\":$size,\"items\":$count,\"modified\":${f.lastModified()}}","application/json")}catch(_:Exception){respond(o,"Not found","text/plain",404)}}
/** Reads a JSON array of strings for one key. Values are unescaped minimally. */
private fun jsonStringArray(j:String,key:String):List<String>{val m=Regex("\"$key\"\\s*:\\s*\\[([^\\]]*)\\]").find(j)?:return emptyList();return Regex("\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(m.groupValues[1]).map{it.groupValues[1].replace("\\\"","\"").replace("\\\\","\\")}.toList()}
private fun deletePath(o:BufferedOutputStream,r:Request){try{val f=safePath(r.query["path"]?:"")?:throw IOException();if(f.isDirectory)f.deleteRecursively() else if(!f.delete())throw IOException();respond(o,"{\"ok\":true}","application/json")}catch(_:Exception){respond(o,"Invalid path","text/plain",400)}}

 private fun chunk(o:BufferedOutputStream,i:InputStream,r:Request){try{val id=r.query["transfer_id"]?:throw IOException();val fid=r.query["file_id"]?:throw IOException();val offset=(r.query["offset"]?:"0").toLong();val size=r.headers["content-length"]?.toLongOrNull()?:throw IOException();val total=r.headers["x-file-size"]?.toLongOrNull()?:throw IOException();if(total !in 1..maxFile||size !in 1..maxChunk||offset<0||offset+size>total)throw IOException();val dest=safePath(fid)?:throw IOException();val key=id;val temp=File(root,".$key.part");val t=transfers.computeIfAbsent(key){Transfer(key,fid,fid.substringAfterLast('/'),total,r.headers["x-sha256"]?:throw IOException(),temp)};if(t.fileId!=fid||t.size!=total||t.expected!=r.headers["x-sha256"]||offset!=t.received)throw IOException();temp.parentFile?.mkdirs();RandomAccessFile(temp,"rw").use{it.seek(offset);val b=ByteArray(8192);var left=size;while(left>0){val n=i.read(b,0,minOf(b.size.toLong(),left).toInt());if(n<0)throw EOFException();it.write(b,0,n);left-=n}};t.received+=size;if(t.received==t.size){if(!t.expected.equals(sha256(temp),true))throw IOException("hash mismatch");dest.parentFile?.mkdirs();val final=if(dest.exists())safePath("${fid.substringBeforeLast('/',"").let{if(it.isEmpty())"" else "$it/"}}${System.currentTimeMillis()}-${dest.name}")?:throw IOException() else dest;if(!temp.renameTo(final))throw IOException();transfers.remove(t.id);onTransfer?.invoke(final.name,t.size,false);respond(o,"{\"complete\":true,\"file_id\":\"${escape(fid)}\",\"sha256\":\"${sha256(final)}\"}","application/json",201)}else respond(o,"{\"transfer_id\":\"${escape(t.id)}\",\"offset\":${t.received},\"complete\":false}","application/json")}catch(_:Exception){respond(o,"Invalid chunk","text/plain",400)} }
 private fun downloadChunk(o:BufferedOutputStream,r:Request){val f=safeFile(r.query["file_id"]?:"");if(f==null||!f.isFile){respond(o,"Not found","text/plain",404);return};val start=(r.query["offset"] ?: "0").toLongOrNull()?:0;val length=(r.query["length"]?:"${f.length()-start}").toLongOrNull()?:0;if(start<0||length<0||start+length>f.length()||length>maxChunk){respond(o,"Invalid range","text/plain",416);return};o.write("HTTP/1.1 206 Partial Content\r\nContent-Type: application/octet-stream\r\nContent-Length: $length\r\nContent-Range: bytes $start-${start+length-1}/${f.length()}\r\nConnection: close\r\n\r\n".toByteArray());RandomAccessFile(f,"r").use{it.seek(start);val b=ByteArray(8192);var left=length;while(left>0){val n=it.read(b,0,minOf(b.size.toLong(),left).toInt());if(n<0)break;o.write(b,0,n);left-=n}};o.flush();if(start+length>=f.length())onTransfer?.invoke(f.name,f.length(),true)}
 private fun field(j:String,n:String)=Regex("""\"$n\"\s*:\s*\"([^\"]*)\"""").find(j)?.groupValues?.get(1)?:""
 private fun sha256(f:File)=MessageDigest.getInstance("SHA-256").let{d->f.inputStream().use{b->val x=ByteArray(8192);while(true){val n=b.read(x);if(n<0)break;d.update(x,0,n)}};d.digest().joinToString(""){"%02x".format(it)}}
 private fun listFiles()=root.listFiles().orEmpty().filter{it.isFile&&!it.name.startsWith(".upload-")&&!it.name.startsWith(".")}.joinToString(",","["){ "{\"id\":\"${escape(it.name)}\",\"name\":\"${escape(it.name)}\",\"size\":${it.length()}}" }+"]"
 private fun escape(s:String)=s.replace("\\","\\\\").replace("\"","\\\"").replace("\r"," ").replace("\n"," ")
 /** Streams a file from the drop folder. Accepts nested paths like folder/file.txt. [fromPreview] is true when a browser merely rendered the file (e.g. an image thumbnail), which should not count as a transfer. */
 private fun download(o:BufferedOutputStream,rawPath:String,fromPreview:Boolean=false){val f=safePath(URLDecoder.decode(rawPath,"UTF-8"));if(f==null||!f.isFile){respond(o,"Not found","text/plain",404);return};o.write("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Disposition: attachment; filename=\"${f.name.replace("\"","")}\"\r\nContent-Length: ${f.length()}\r\nConnection: close\r\n\r\n".toByteArray());f.inputStream().use{it.copyTo(o)};o.flush();if(!fromPreview)onTransfer?.invoke(f.name,f.length(),true)}
 /** Small JPEG preview for grid thumbnails (?path=folder/img.jpg&w=360). Downscaling here keeps the browser from pulling full photos and protects the phone from mass grid loads. */
 private fun thumbnail(o:BufferedOutputStream,r:Request){try{val f=safePath(r.query["path"]?:"");if(f==null||!f.isFile){respond(o,"Not found","text/plain",404);return};val want=(r.query["w"]?:"360").toIntOrNull()?.coerceIn(64,1024)?:360;val opts=android.graphics.BitmapFactory.Options().apply{inJustDecodeBounds=true};android.graphics.BitmapFactory.decodeFile(f.absolutePath,opts);if(opts.outWidth<=0||opts.outHeight<=0){respond(o,"Not an image","text/plain",404);return};var sample=1;var w=opts.outWidth;var h=opts.outHeight;while(w/2>=want&&h/2>=want){sample*=2;w/=2;h/=2};val bo=android.graphics.BitmapFactory.Options().apply{inSampleSize=sample};val bmp=android.graphics.BitmapFactory.decodeFile(f.absolutePath,bo)?:run{respond(o,"Decode failed","text/plain",500);return};val bos=java.io.ByteArrayOutputStream();bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG,80,bos);bmp.recycle();val x=bos.toByteArray();o.write("HTTP/1.1 200 OK\r\nContent-Type: image/jpeg\r\nCache-Control: max-age=3600\r\nContent-Length: ${x.size}\r\nConnection: close\r\n\r\n".toByteArray());o.write(x);o.flush()}catch(_:Exception){respond(o,"Thumb error","text/plain",500)}}
 /** Browser upload into the folder open on screen: multipart with a "file" part; folder comes from ?path=. */
 private fun uploadToFolder(o:BufferedOutputStream,i:BufferedInputStream,r:Request){
  val dir=safeDirectory(r.query["path"]?:"")?:run{respond(o,"Invalid folder","text/plain",400);return}
  val length=r.headers["content-length"]?.toLongOrNull()?:0L
  if(length !in 1..(maxFile+64*1024)){respond(o,"Invalid size","text/plain",413);return}
  val ct=r.headers["content-type"]?:""
  val boundary=ct.substringAfter("boundary=","").trim().trim('"')
  if(!ct.startsWith("multipart/form-data",true)||boundary.isBlank()||boundary.length>200){respond(o,"Invalid multipart","text/plain",400);return}
  val end=("\r\n--$boundary").toByteArray(Charsets.ISO_8859_1)
  var remaining=length
  val temp=File(root,".upload-${System.nanoTime()}.tmp")
  try{
    val hdr=ByteArrayOutputStream()
    while(true){val b=i.read();if(b<0)throw EOFException();remaining--;hdr.write(b);val a=hdr.toByteArray();if(a.size>=4&&a[a.size-4]=='\r'.code.toByte()&&a[a.size-3]=='\n'.code.toByte()&&a[a.size-2]=='\r'.code.toByte()&&a[a.size-1]=='\n'.code.toByte())break;if(a.size>16*1024)throw IOException("headers too large")}
    val hs=hdr.toString("UTF-8")
    val fn=Regex("""filename="([^"]+)"""").find(hs)?.groupValues?.get(1)?:throw IOException("no filename")
    val name=fn.substringAfterLast('/').substringAfterLast('\\')
    if(name.isBlank()||name.startsWith("."))throw IOException("bad filename")
    temp.outputStream().buffered().use{out->
      val window=ArrayDeque<Byte>();var found=false
      while(remaining>0){val b=i.read();if(b<0)throw EOFException();remaining--;window.addLast(b.toByte());if(window.size>end.size)out.write(window.removeFirst().toInt());if(window.size==end.size&&window.toByteArray().contentEquals(end)){found=true;break}}
      if(!found)throw IOException("no closing boundary")
    }
    val base=root.canonicalFile
    var dest=File(dir,name).canonicalFile
    if(!dest.path.startsWith(base.path+File.separator))throw IOException("outside root")
    if(dest.exists())dest=File(dir,"${System.currentTimeMillis()}-$name")
    if(!temp.renameTo(dest))throw IOException("rename failed")
    onTransfer?.invoke(dest.name,dest.length(),false)
    while(remaining>0){if(i.read()<0)break;remaining--}
    respond(o,"{\"ok\":true,\"name\":\"${escape(dest.name)}\"}","application/json",201)
  }catch(_:Exception){temp.delete();respond(o,"Invalid upload","text/plain",400)}
 }
 /**
  * Browser upload: multipart/form-data with one "file" part. Parses the body in a
  * streaming way: headers first, then file bytes until the closing boundary.
  */
 private fun upload(o:BufferedOutputStream,i:BufferedInputStream,r:Request){
  val length=r.headers["content-length"]?.toLongOrNull()?:0L
  if(length !in 1..(maxFile+64*1024)){respond(o,"Invalid size","text/plain",413);return}
  val ct=r.headers["content-type"]?:""
  if(!ct.startsWith("multipart/form-data",true)){respond(o,"Invalid multipart","text/plain",400);return}
  val boundary=ct.substringAfter("boundary=","").trim().trim('"')
  if(boundary.isBlank()||boundary.length>200){respond(o,"Invalid multipart","text/plain",400);return}
  val delim="--$boundary".toByteArray(Charsets.ISO_8859_1)
  val partSep="\r\n\r\n".toByteArray(Charsets.ISO_8859_1)
  var remaining=length
  val temp=File(root,".upload-${System.nanoTime()}.tmp")
  var name:String?=null
  try{
    // 1) Read part headers until blank line.
    val hdr=ByteArrayOutputStream()
    while(true){
      val b=i.read(); if(b<0) throw EOFException(); remaining--
      hdr.write(b)
      val a=hdr.toByteArray()
      if(a.size>=4 && a.copyOfRange(a.size-4,a.size).contentEquals(partSep)) break
      if(a.size>16*1024) throw IOException("headers too large")
    }
    val hs=hdr.toString("UTF-8")
    val fn=Regex("""filename="([^"]+)"""").find(hs)?.groupValues?.get(1)?:throw IOException("no filename")
    name=fn.substringAfterLast('/').substringAfterLast('\\')
    val dest0=safeFile(name)?:throw IOException("bad filename")
    // 2) Stream file bytes until "\r\n--boundary", keeping a sliding window.
    val end=("\r\n"+String(delim,Charsets.ISO_8859_1)).toByteArray(Charsets.ISO_8859_1)
    temp.outputStream().buffered().use{out->
      val window=ArrayDeque<Byte>()
      var found=false
      while(remaining>0){
        val b=i.read(); if(b<0) throw EOFException(); remaining--
        window.addLast(b.toByte())
        if(window.size>end.size){ out.write(window.removeFirst().toInt()) }
        if(window.size==end.size && window.toByteArray().contentEquals(end)){ found=true; break }
      }
      if(!found) throw IOException("no closing boundary")
    }
    val dest=if(dest0.exists()) safeFile("${System.currentTimeMillis()}-${dest0.name}")?:throw IOException() else dest0
    if(!temp.renameTo(dest)) throw IOException("rename failed")
    onTransfer?.invoke(dest.name,dest.length(),false)
    // Drain the rest of the request (closing "--" and CRLF) so the socket closes cleanly.
    while(remaining>0){ if(i.read()<0) break; remaining-- }
    respond(o,"{\"ok\":true,\"name\":\"${escape(dest.name)}\"}","application/json",201)
  }catch(_:Exception){
    temp.delete()
    respond(o,"Invalid upload","text/plain",400)
  }
 }
 private fun safeFile(n:String):File?{if(n.isBlank()||n.length>240||n.contains("..")||n.contains('/')||n.contains('\\')||n.any{it.isISOControl()})return null;return File(root,n).canonicalFile.takeIf{it.parentFile==root.canonicalFile}}
 private fun readHeaders(i:BufferedInputStream):Request?{val b=ByteArrayOutputStream();var m=0;while(b.size()<16*1024){val x=i.read();if(x<0)return null;b.write(x);m=when{m==0&&x==13->1;m==1&&x==10->2;m==2&&x==13->3;m==3&&x==10->4;x==13->1;else->0};if(m==4)break};if(m!=4)return null;val l=b.toString("ISO-8859-1").trimEnd('\r','\n').split("\r\n");val f=l.first().split(' ');if(f.size<2)return null;return Request(f[0],f[1],l.drop(1).mapNotNull{val p=it.indexOf(':');if(p<1)null else Pair(it.substring(0,p).lowercase(),it.substring(p+1).trim())}.toMap())}
 private fun respond(o:BufferedOutputStream,b:String,type:String="text/plain",s:Int=200){val x=b.toByteArray();o.write("HTTP/1.1 $s ${if(s==200)"OK" else "Error"}\r\nContent-Type: $type\r\nContent-Length: ${x.size}\r\nConnection: close\r\n\r\n".toByteArray());o.write(x);o.flush()}
 /** Prefers the Tailscale address (100.64.0.0/10) so a laptop on the same tailnet can reach the phone. */
private fun lanAddress():String{
  val ips=NetworkInterface.getNetworkInterfaces().toList().flatMap{it.inetAddresses.toList()}.filter{it is Inet4Address&&!it.isLoopbackAddress}.map{it.hostAddress}
  return ips.firstOrNull{it.startsWith("100.")}?:ips.firstOrNull{it.startsWith("192.168.")||it.startsWith("10.")}?:ips.firstOrNull()?:"127.0.0.1"
}
 private data class Request(val method:String,val target:String,val headers:Map<String,String>){val path get()=target.substringBefore('?');val query get()=target.substringAfter('?').split('&').mapNotNull{val x=it.split('=',limit=2);if(x.size==2)Pair(x[0],URLDecoder.decode(x[1],"UTF-8"))else null}.toMap()}
}
class AutoStopController(private val stop:()->Unit){private var deadline=0L;fun arm(minutes:Int){require(minutes in 1..120);deadline=System.currentTimeMillis()+minutes*60000L};fun tick(now:Long=System.currentTimeMillis()){if(deadline!=0L&&now>=deadline){deadline=0;stop()}}}
interface LocalTransferServer{fun start(token:SessionToken):Int;fun stop();fun isRunning():Boolean}
