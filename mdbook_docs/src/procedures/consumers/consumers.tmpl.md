# Consumers

A Procedure that takes in some data but doesn't return any data.

{{EX1}}

<div class="warning">

Note: Consumers tend to be an inherent waste of computation time __unless__ that consumer does some side-effecting
operation observable outside the program scope. So, it may be a useful hint that if you're reading code that includes a
call to a consumer, some I/O is very likely taking place (if not, you should delete the call entirely as it's a waste of
work).
</div>