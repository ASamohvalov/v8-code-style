# Reference to a Constructor Function

For parameters of Structure, ValueTable, and ValueTree types, the type description must include a reference to the function that returns this structure, table, or tree (e.g., a reference to its constructor function).The validation lint tool considers it an error to specify generic Structure/ValueTable/ValueTree types if the method body accesses specific properties or keys of that variable.

## Incorrect

```bsl
// Structure acceptance
// Parameters:
// 		UserStructure - Structure
Function AcceptStructure(UserStructure) 

	UserStructure.Insert("Name", "Value");

EndFunction

```

## Correct

```bsl
// Structure acceptance
// Parameters:
// 		UserStructure - See StructureConstructor
Function AcceptStructure(UserStructure) 

	UserStructure.Insert("Name", "Value");

EndFunction

```

## See Also

[Description of Procedures and Functions, sec. 5.2.2](https://its.1c.ru/db/v8std#content:453:hdoc)
