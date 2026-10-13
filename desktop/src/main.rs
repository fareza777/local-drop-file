// Tell Windows this is a GUI app: without this line the binary is built as a
// console app and a black cmd window opens next to the real window.
#![windows_subsystem = "windows"]

fn main() { localdrop_desktop_lib::run(); }
