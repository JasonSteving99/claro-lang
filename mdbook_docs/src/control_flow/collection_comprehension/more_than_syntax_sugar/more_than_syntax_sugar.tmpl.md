# Comprehension is More Than Syntax Sugar

While the previous section emphasized the ergonomic and simplifying qualities of Comprehensions, it should be explicitly
stated that this construct is **not just syntax sugar**. Specifically, there is no other way in the language to directly
initialize a List/Set/Map with size and elements determined dynamically at runtime **without incurring an extra copy**:

{{EX1}}

Using List Comprehension instead not only produces much simpler code, but will also allow you to drop the unnecessary 
copy:

{{EX2}}

<div class="warning">

**Note**: Read more about Claro's built-in `copy(...)` operator here (TODO(steving)).
</div>