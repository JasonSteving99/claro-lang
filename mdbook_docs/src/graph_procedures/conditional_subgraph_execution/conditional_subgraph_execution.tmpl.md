# (Advanced) Conditional Subgraph Execution

There will be times when you actually only want to execute some *portion* of the graph upon satisfying some condition.
In this case, you may inject the node to a procedure expecting a `provider<future<...>>` so that you may conditionally
trigger execution yourself after checking the condition:

{{EX1}}

__Read closely above__. The `getOptionalUpgradeBannerFromDB()` function above expects an arg of type 
`provider<future<Html>>` which is injected as a lazy subgraph rooted at node `upgradeBanner`. In this way, two of the
nodes within the overall `getHomepage()` graph will only run conditionally upon determining that the user is not already
a "premium" member.

<pre class="mermaid">
    graph TD
    basePage --> homePage
    maybeUpgradeBanner --> homePage
    userIsPremium --> maybeUpgradeBanner
    upgradeBanner -.-> maybeUpgradeBanner
    specialOffers -.-> upgradeBanner
    subgraph Conditional Subgraph
        upgradeBanner
        specialOffers
    end 
</pre>