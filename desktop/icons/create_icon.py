from PIL import Image, ImageDraw
im=Image.new('RGBA',(256,256),(17,24,39,255))
d=ImageDraw.Draw(im)
d.rounded_rectangle((24,24,232,232), radius=48, fill=(34,211,238,255))
d.polygon([(64,128),(116,76),(116,106),(192,106),(192,150),(116,150),(116,180)], fill=(15,23,42,255))
im.save(r'C:\Local Drop File Transfer\desktop\icons\icon.ico', sizes=[(256,256),(128,128),(64,64),(48,48),(32,32),(16,16)])
