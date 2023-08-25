package tools.redstone.picasso.util.data;

/** Carries a mutable value on the heap */
public class Container<T> {

    public T value;

    public Container() { }
    public Container(T value) { this.value = value; }

}
