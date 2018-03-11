gudb
=====
Welcome to gudb, a prettier gdb interface. 


(Inspired by pudb, the awesome python debugger.)

### Requirements

1. gdb (> v7.3)
2.  python2 (>= v2.7, pip install future)
    or
    python3 (>= 3.0)

### Requirements for building from source

1. node.js
2. npm

### How to build from source

```bash
$ git clone https://github.com/typon/gudb 
$ cd gudb
$ make build
$ make run PROG=<path to program executable>
```

### How to create binary release for your platform

1. Ensure your platform is supported by [pkg](https://github.com/zeit/pkg).
2. Run the following commands:

```bash
$ git clone https://github.com/typon/gudb 
$ cd gudb
$ make release PLATFORM=<pkg-platform-string> # E.g. PLATFORM=latest-linux-x64
$ ./gudb-<pkg-platform-string>.exe
```