gudb
=====
Welcome to gudb, a prettier gdb interface. 


Read what others have to say about it!

"It helped marginally increase my productivity because I sometimes debug over ssh."
    - My cousin

"Who the hell still writes C?"
    - Reddit comment

"Stop emailing me you weirdo."
    - Rich Hickey

### Requirements

1. gdb (> v7.3)
2.  python2 (>= v2.7, pip install future)
    or
    python3 (>= 3.0)

### Requirements for building from source

1. build-essential (i.e. gcc, Make, etc.)
2. node.js (>= 9.0)
3. npm
4. Java Development Kit (>= 8.0)

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
