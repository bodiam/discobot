Global AST Transformation Example

This example shows how to wire together a global transformation. 

The example requires ant in your path and the Groovy 1.6 (or greater) 
Jar in your classpath. 

To build the example run "ant" from the current directory. The default 
target will compile the classes needed. The last step of the build 
script prints out the command needed to run the example. 

To run the first example perform the following from the command line: 
  groovy -cp LoggingTransform.jar LoggingExample.groovy
  
The example should print: 
  Starting greet
  Hello World
  Ending greet

To run the second example perform the following from the command line: 
  groovy -cp LoggingTransform.jar CompiledAtExample.groovy
  
The example should print: 
  Scripted compiled at: [recently]
  Class compiled at: [recently]

No exceptions should occur. 