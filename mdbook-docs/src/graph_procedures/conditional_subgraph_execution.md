# (Advanced) Conditional Subgraph Execution

There will be times when you actually only want to execute some *portion* of the graph upon satisfying some condition.
In this case, you may inject the node to a function expecting a `Provider<future<...>>` so that you may conditionally
trigger execution yourself after checking the condition:

```
graph function getHomepage(userId : UserId) -> future<Homepage> {
    root homepage <- renderPage(@basePage, @maybeUpgradeBanner);
    node basePage <- getBasePageFromDB();
    node maybeUpgradeBanner
        <- getOptionalUpgradeBannerFromDB(
               @userIsPremium,
               @upgradeBanner  # <-- "Lazy Subgraph" injection requested.
           ); 
    node userIsPremium <- checkPremiumFromDB(userId);
    node upgradeBanner <- getUpgradeBannerFromDB();
}

...

function getOptionalUpgradeBannerFromDB(
    alreadyPremium: boolean,
    getUpgradeBannerPromDBProvider: provider<future<Upgrade>>
) -> Optional<future<Upgrade>> {
    if (already premium) {
        return Nothing;
    }
    return getUpgradeBannerFromDBProvider();
}
```

__Read closely above__. The function shown requests an arg of type `provider<future<Upgrade>>` which is injected as a
lazy subgraph rooted at node `upgradeBanner`. In this way, the subgraph of the `getHomepage(...)` graph is only run
sometimes, upon satisfying the condition that the user is not already a "premium" member.

### Note on Usage of `Optional` in Above Example:

If you read the above example very closely, you may have noticed that the return type of
the `getOptionalUpgradeBannerFromDB(...)` is `Optional<future<Upgrade>>` but yet the two return statements in the
function are `return Nothing;` and `return getUpgradeBannerFromDBProvider();`, neither of which reference `Optional`.
This is making use of a type system feature upcoming very soon in the language but not yet available, `oneof<...>`
types. I've done this to make a less distracting example, but for now, until `oneof<...>` is available, you would
actually need to do a workaround to define your own quasi `Optional` perhaps looking something like:

```
# Impl of `Optional` before `oneof<...>` type system support.
alias Optional : tuple<boolean, future<Upgrade>, NothingType>
```

As a preview, for anyone very interested in what the definition of this more convenient `Optional` in the example above
would look like making use of the in-development `oneof<...>` type:

```
# Impl of `Optional` using `oneof<...>`.
alias Optional<T> : oneof<T, !! NothingType>
```

Stay tuned for updates on `oneof<...>` support.