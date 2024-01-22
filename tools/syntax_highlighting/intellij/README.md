# Configuring Syntax Highlighting in IntelliJ

Configuring Claro syntax highlighting in IntelliJ is currently takes a very minimalistic approach. You'll simply define
a new custom language and then define a few groups of keywords and tokens for IntelliJ to color.

## 1. Define a New Claro File Type

Navigate to **Preferences > Editor > File Types** and click the `+` to add a new custom file type. Call it "Claro". 

The new file type's basic settings should match those in the below screenshot:

![Add New IntelliJ File Type](./Add%20New%20IntelliJ%20File%20Type.png)

## 2. Configure Keyword Groups

Under the `keywords` heading, add the following keyword groups:

#### Group 1
```
alias atom bind blocking break consumer continue else false flag for function graph if immutable lazy match module newtype node opaque provider repeat return root static to true var where while HttpService 
```
#### Group 2
```
!= * + ++ , - -- -> / : ; < <- <= = == > >= ?= @ ^ |> and as cast contract copy endpoint_handlers fromJson getHttpClient implement in initializers instanceof mut not or remove requires sleep unwrap unwrappers
```
#### Group 3
```
Error HttpClient Nothing ParsedJson _ boolean char double float future int lambda long oneof string struct tuple
```
#### Group 4
```
? case using |
```

## 3. (Optional) Configure Keyword Group Colors

IntelliJ also allows you to explicitly reconfigure the coloring of each of the declared keyword groups. To do so, go to
**Editor > Color Scheme > User-Defined File Types**.

![Configure Colors](./Configure%20Colors.png)