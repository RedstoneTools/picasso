package tools.redstone.picasso.util.data;

import java.util.Stack;

/**
 * Extended delegating stack.
 */
public class ExStack<T> extends Stack<T> implements Cloneable {

    /** Pop an element or null if none are available */
    public T popOrNull() {
        return isEmpty() ? null : pop();
    }

    /** Peek an element or null if none are available */
    public T peekOrNull() {
        return isEmpty() ? null : peek();
    }

}
