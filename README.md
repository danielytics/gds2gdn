# gds2gdn
A GDScript to GDNative-C++ transpiler

This project aims to parse GDScript source code and generate `godot-cpp` compatible GDNative C++ code and the matching `.gdns` file. The user will still need to compile the C++ code and create their own platform-specific `.gdnlib` files.

# Work In Progress Usage

Currently, no effort has been made to make this project easy to run for others. The easiest way to run the WIP code is to download the `instaparse-lumo` jar file, install `lumo` and run:

```
lumo -c instaparse-lumo-1.4.7.jar -m gds2gdn.core test.gd
```

replacing `instaparse-lumo-1.4.7.jar` with the name and version of the `instaparse-lumo` version you downloaded and `test.gd` with your GDScript file.

Currently it only parses the source file and outputs an AST in EDN format. As this project matures (ie once the grammar and tree transformation logic is implemented), it will begin to generate C++ output.

# Limitations

 - Currently `gds2gdn` works on single `.gd` files as self-contained units. In time, it will be smarter and follow `extends` references to other scripts.
 - There are some GDScript features which either aren't (yet, in some cases) supported by `godot-cpp` (for example, many of the GDScript built-in functions) and these are therefore not supported by `gds2gdn`. Support will be added as `godot-cpp` matures.
 
