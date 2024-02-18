# Visibility

Claro's deep integration with Bazel allows it to isolate all dependency-related concerns to the Build system rather than
cluttering the core language itself with such concerns. Claro's leveraging of Bazel's builtin visibility enforcement
features is a powerful example of this. Whereas most programming languages tend to only expose very coarse-grained
visibility controls (e.g. public/private), Bazel provides Claro programs with access to a wide range of extremely
fine-grained visibility controls.

You can read more in detail about 
<a href="https://bazel.build/concepts/visibility" target="_blank">Visibility in Bazel's docs</a>, or you can get the
important overview below. 

<div class="warning">

If you're using GitHub to host your codebase, Bazel's Visibility enforcement becomes _even more powerful_ when used in
combination with 
<a href="https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/about-code-owners" target="_blank">`CODEOWNERS`</a>
which gives you a mechanism to ensure that Visibility declarations are actually enforceable and can't simply be changed
by someone without first taking into account _why_ the visibility was constrained to a certain level in the first place.
</div>

## Private Visibility

All targets are "private" by default. So, if you don't want to allow any other targets defined outside the current BUILD
file to take a dependency on a given target, you can simply omit a `visibility = ...` declaration:

{{EX1}}

<div class="warning">

**Note**: All targets defined in the same `BUILD` file are implicitly Visible to each other - meaning that they can
place a dependency on one another freely no matter what `visiblity = ...` declaration is listed in each target's
declaration.
</div>

## Target Visibility

In many cases, you'll be designing Modules to only be consumed by a very specific set of dependents. In this case, you
can **explicitly name the specific targets** that should be allowed to place a dependency on your target:

{{EX2}}

**This is by far the recommended approach. All other more permissive Visibility declarations detailed below should be
approached with caution.**

## Package Visibility

Depending on how your codebase gets organized, you'll likely run into the situation where a certain Module can be
generally useful for many Modules in a package. Rather than needing to list each target individually, you can whitelist
the entire package to have Visibility on a certain module.

{{EX3}}

## Subpackages Visibility

Somewhat more rarely, you may also end up with a codebase where a particular Module is useful for many Modules in both a
package and all subpackages beneath it. Rather than needing to explicitly list each package, you can make the target 
Visible to all other targets at or "below" a certain package. 

<div class="warning">
This should be used sparingly when you have confidence that the design constraints of your codebase will be maintained
over time. Remember that the proliferation of many dependencies on a particular Module put that Module at risk at
becoming extremely difficult to change in the future.
</div>

{{EX4}}

## Public Visibility (Discouraged)

<div class="warning">

While it's possible to make a certain target visible to **every** other target in the entire project, **this is
discouraged**. You will find that public Visibility can lead to a proliferation of dependencies that can sometimes make 
the long-term maintenance of the overall project that much more difficult. The more dependencies that a particular
Module has, the more difficult it gets to make any changes to that Module's public API. Feel free to use this feature, 
but please do it consciously, don't just get in a habit of doing this by default for convenience.
</div>

{{EX5}}
