# Basic HTTP Servers

Claro has been carefully designed to be uniquely well-suited for building highly efficient, scalable web servers.
Claro's ["Fearless Concurrency"](../fearless_concurrency/fearless_concurrency.md) guarantees are explicitly an effort to
make it significantly challenging to make a buggy, slow web server - and instead, Claro's novel concurrency model will
lead to performant and easy-to-maintain web servers naturally falling out of even naive usages of the language.

To actually demonstrate this explicitly, Claro provides very basic support for building HTTP servers that can be used in
the absence of any sort of 3rd party framework to jump you right into your first web server in Claro. This feature is
largely intended as a demonstration of Claro's current capabilities, and to point towards Claro's future direction. This
is by no means a complete web server framework.

Following this guide will lead you through the steps to setting up your very first web server in Claro.

## HTTP Service Definition

First, you'll need to define the endpoints that your HTTP service will handle. To do this, you'll use Claro's built-in
`HttpService` definition syntax, e.g.:

{{EX1}}

The above defines a very simple service with two basic endpoints.

## Auto-Generated HttpServer

Claro will automatically generate a pre-configured, non-blocking web server implementation for your `HttpService`
definition by using the builtin magic function `http::getBasicHttpServerForPort()`. This function is implemented as a
compiler intrinsic that will infer the server to automatically generate based on the type asserted on the call. So, we
can get Claro to generate a web server for the example `Greeter` service as in the example below.

<div class="warning">
Note that no Endpoint Handlers have been implemented yet so we should actually expect the below to fail to compile and
prompt us to implement them! Doing things in this order allows us to allow Claro to prompt us with the signatures that
we need to implement, which is just a convenience.
</div>

{{EX2}}

## Implementing Endpoint Handlers

A service definition on its own doesn't actually provide much utility without endpoint handlers implemented to actually
serve up the responses to incoming requests. As we see from the compilation error above, we must define endpoint
handlers for the above HttpService by defining an `endpoint_handlers` block with a Graph Procedure implementation
corresponding to each endpoint in the HttpService definition. 

Note that in the HTTP service definition above, the `greeting` endpoint includes `{name}` in the route - this is a "path 
parameter" that will automatically be parsed from incoming requests and passed along as input to the associated endpoint
handler. So, note that the signature of the `greeting` endpoint handler includes a `string` arg that will represent the
value of the `{name}` path parameter for each request to that endpoint. 

{{EX3}}

As you can see, the core implementation logic was factored out into another Module `EndpointHandlerImpls`. These impls
can do anything, including making arbitrary downstream network requests, as long as they are non-blocking. In this case,
they'll simply return some simple greeting.

<div class="warning">
Note: the requirement that each endpoint handler implementation be a Graph Procedure is to ensure that the resulting
web service is statically guaranteed to be non-blocking <b>and</b> to ensure that each request is handled <b>off the
request thread</b> so that long-running computations don't interfere with the service's ability to receive and schedule 
incoming requests. This ties together all of Claro's design decisions to make building fundamentally concurrent web
services a trivial task.
</div>

## Starting an `HttpServer`

That's it! Now we can actually _start_ the `Greeter` server that we just implemented. This is as simple as calling the
builtin `http::startServerAndAwaitShutdown()` consumer. This call effectively drops into an infinite loop, so depending
on how you start it, when you're done and want to bring the service down, you'll have to send a termination signal to
the server process e.g. using ctrl-C.

{{EX4}}

The below recording is a demonstration this server in action. It first starts up the server (launching the process in
the background), and then sends a couple requests to each endpoint using `curl` to demonstrate the server in action, and
then finally kills the server.

_Note: The below recording was made with <a href="https://asciinema.org/" target="_blank">asciinema</a> - try pausing
and copying any text._
<script async id="asciicast-640744" src="https://asciinema.org/a/640744.js" data-preload="true" data-autoplay="false"></script>
