" https://learnvimscriptthehardway.stevelosh.com/chapters/44.html

if exists("b:current_syntax")
    finish
endif

syntax match claroConstant "\v[a-zA-Z][a-zA-Z0-9]*"

syntax match claroType "\v\["
syntax match claroType "\v\]"
syntax match claroType "\v\{"
syntax match claroType "\v\}"
syntax keyword claroType NothingType int boolean float future lambda string

syntax keyword claroConditional else if match case or and not return
syntax keyword claroRepeat for while break continue repeat

syntax match claroNumber "\v[0-9]\.?"

syntax match claroCharacter "\v\'.\'"

syntax match claroOperator "\v\="
syntax match claroOperator "\v\!\="
syntax match claroOperator "\v\?\="
syntax match claroOperator "\v\+\+"
syntax match claroOperator "\v--"
syntax match claroOperator "\v,"
syntax match claroOperator "\v:"
syntax match claroOperator "\v;"
syntax match claroOperator "\v\^"
syntax match claroOperator "\v\*"
syntax match claroOperator "\v\/"
syntax match claroOperator "\v\+"
syntax match claroOperator "\v-"
syntax match claroOperator "\v-\>"
syntax match claroOperator "\v\<-"
syntax match claroOperator "\v\>"
syntax match claroOperator "\v\<"
syntax match claroOperator "\v\>\="
syntax match claroOperator "\v\<\="
syntax match claroOperator "\v\=\="

syntax match claroIdentifier "\v[a-zA-Z0-9]*"

syntax match claroFunction "\v^[a-z][a-zA-Z0-9]*"

syntax region claroComment start="#" end="\n"

highlight link claroIdentifier Identifier
highlight link claroType Type
highlight link claroCharacter Character
highlight link claroNumber Number
highlight link claroConstant Constant
highlight link claroComment Comment
highlight link claroFunction Function
highlight link claroKeyword Keyword
highlight link claroOperator Operator
highlight link claroConditional Conditional
highlight link claroRepeat Repeat

let b:current_syntax = "claro"

