# Your First Program

Now that you've you've set up your starter Claro project in the previous section, let's go through the process of 
implementing your first program!

## Create `hello_world.claro`

{{EX1}}

Hello World is a one-liner in Claro, so it's a great place to start learning how to declare a new Claro program using
Bazel. Just to keep things simple, copy the above line into a new file at `//example/hello_world.claro`.

{{EX2}}

## Declare a New `claro_binary(...)` Target in Your BUILD File

Now, we'll simply add a new build target for our Hello World program to the existing BUILD file that was generated as
part of the starter project.

{{EX3}}

## Now Execute Your Program! 

That's all there is to it! Now you can use the following command to have Bazel build and then run your program:

_Note: The below recording was made with <a href="https://asciinema.org/" target="_blank">asciinema</a> - try pausing
and copying any text._
<script async id="asciicast-bhFOGiVy3oQT07drghvf5Js5o" src="https://asciinema.org/a/bhFOGiVy3oQT07drghvf5Js5o.js" data-preload="true"></script>

**Congratulations!** You just wrote and executed your first Claro program entirely from scratch!

### Avoiding Bazel's Extra Output

Notice that when you used `bazel run ...` to run your executable build target, Bazel produced a bunch of `INFO: ...`
logs related to the build process. Since the program built successfully, this is something that you can usually just
ignore. However, if this extra noise bothers you, you can make use of Bazel's generated build artifacts to run the 
program directly, without any of Bazel's extra logging. Notice the very last line in Bazel's output:

```
INFO: Running command line: bazel-bin/example/hello_world
```

This is a script that can be directly invoked to run the built executable program locally.
<div class="warning">
This is not a portable executable! <b>Continue reading to learn how to generate a portable executable.</b>
</div>

_Note: The below recording was made with <a href="https://asciinema.org/" target="_blank">asciinema</a> - try pausing
and copying any text._
<script async id="asciicast-630572" src="https://asciinema.org/a/630572.js" data-preload="true" data-start-at="7.0" data-autoplay="false"></script>

### Generating a Portable Executable ("Deploy Jar")

As Claro is a JVM language, you can easily generate a self-contained Jar file that can be run anywhere that a JVM is
installed. Generate the "Deploy Jar" by appending `_deploy.jar` to the end of any `claro_binary()` build target, and can
then run it using `java -jar ...` as you would any executable Jar:

_Note: The below recording was made with <a href="https://asciinema.org/" target="_blank">asciinema</a> - try pausing
and copying any text._
<script async id="asciicast-630573" src="https://asciinema.org/a/630573.js" data-preload="true"></script>

<div class="warning">
<b>Warning</b>: The `java -jar ...` command demonstrated above will make use of your local Java installation. Assuming 
that you've kept the flag `common --java_runtime_version=remotejdk_11` in your .bazelrc as described in the previous 
section, you may have been running Claro programs without even manually installing Java, meaning that this command will 
fail. Generally speaking, you shouldn't worry about this as it's encouraged to use `bazel run ...` during local 
development anyway.
</div>