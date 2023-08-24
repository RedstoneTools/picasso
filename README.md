<img src="project/logo650x650.png" width="200" height="200" float="left">
<h2>picasso</h2>
Picasso is a library made to streamline development of advanced,
multi-version APIs and abstractions and greatly reduce the boilerplate in using those.

### An example
[ ] TODO: write docs
```java
/* An example of picasso's most fundamental
 * application, dependency analysis */

// All abstractions/API components inherit from Abstraction
interface ExampleAbstraction implements Abstraction {
    // The call to unimplemented() is picked up by the
    // bytecode analyzer and this method is marked as unimplemented
    // if it isn't overwritten by an impl.
    default String hello() { return unimplemented(); }
    default String world() { return unimplemented(); }
}

// These implementations need to be registered WITHOUT
// loading the classes because of the adapter API.
class ExampleAbstractionImpl extends HandleAbstraction<InternalObject> implements ExampleAbstraction {
    @Override
    public String hello() {
        // Adapt is a method on Abstraction which is replaced
        // by a call to the appropriate adapter by the bytecode transformer.
        return adapt(handle().hello() /* returns, say, a char[] */);
    }
    
    @Override
    public String world() {
        return "World";
    }
}

// Import the dependency usage methods
import static tools.redstone.picasso.usage.Usage;

class Example {
    // This method is marked as required,
    // meaning all dependencies used by it
    // are treated as being required by this class.
    void requiredMethod(ExampleAbstraction a) {
        // ExampleAbstraction.hello is required,
        // because it is directly called.
        // If it is not implemented it should not be loaded
        // and otherwise it will throw a NotImplementedException.
        System.out.print(a.hello());
        
        // ExampleAbstraction.world is optional, because
        // it is called using optionally()
        System.out.print(optionally(() -> {
            return a.world(); // This can also be written inline
        })/* returns an optional */.orElse("WORLD"));
        
        // The first block of code which has all dependencies
        // implemented (left to right) is chosen to be executed.
        // If none are implemented this code should not be run,
        // otherwise it will throw a NoneImplementedException.
        String str = either(a::hello, a::world);
    }
}

/* Your internal system */
class MyFeatureLoader {
    void run() throws Throwable {
        final AbstractionProvider provider = AbstractionManager.getInstance()
                // Enables all the default analysis/transform hooks
                .createDefaultProvider();
        
        // Load implementation classes under package com.example.impl
        new PackageWalker(this.getClass(), "com.example.impl")
                .findResources()
                .filter(r -> r.trimmedName().endsWith("Impl"))
                .forEach(provider::loadAndRegisterImpl);
        
        // Find feature/code/user classes
        final List<String> userClassNames = /* ... */ List.of("com.example.Example");
        userClassNames.stream()
                // load classes
                .map(provider::findClass)
                // filter out classes which dont have their
                // required dependencies implemented
                .filter(provider::allImplemented)
                // do something with the classes remaining
                .forEach(klass -> ...);
    }
}
```