package tools.redstone.picasso.analysis;

public enum RefType {

    /**
     * Denotes a reference to a method. Since this is a member of a class,
     * this will hold the owner/declaring class' name in the class name fields.
     * The name and descriptor will be populated with the method name and descriptor
     * respectively.
     *
     * Example: Object com.example.MyClass.myMethod(int, int)
     * -> className = com.example.MyClass, internalClassName = ...
     * -> name = myMethod
     * -> desc, type = (II)Ljava/lang/Object;
     */
    METHOD,

    /**
     * Denotes a reference to a field. Since this is a member of a class,
     * this will hold the owner/declaring class' name in the class name fields.
     * The name and descriptor will be populated with the field name and descriptor
     * respectively.
     *
     * Example: Object com.example.MyClass.myField
     * -> className = com.example.MyClass, internalClassName = ...
     * -> name = myField
     * -> desc, type = Ljava/lang/Object;
     */
    FIELD,

    /**
     * Denotes a reference to a class. The descriptor will hold the field
     * descriptor for fields with this class as their type. The name field is
     * empty and the class fields hold the class name.
     *
     * Example: com.example.MyClass
     * -> className = com.example.MyClass, internalClassName = ...
     * -> name = null
     * -> desc, type = Lcom/example/MyClass;
     */
    CLASS,

    /**
     * SPECIAL: For the {@link ReferenceInfo#unimplemented()} object.
     */
    UNIMPLEMENTED

}
