package test.picasso;

import org.junit.jupiter.api.Assertions;
import tools.redstone.picasso.AbstractionManager;
import tools.redstone.picasso.AbstractionProvider;
import tools.redstone.picasso.usage.Abstraction;
import tools.redstone.picasso.util.PackageWalker;
import tools.redstone.picasso.util.ReflectUtil;

public class FindImplTest {

    public static void main(String[] args) {
        new FindImplTest().test_FindAndRegisterImpls();
    }

    public interface A extends Abstraction { }
    public interface B extends Abstraction { }

    public static class AImpl implements A { }
    public static class BImpl implements B { }

    void test_FindAndRegisterImpls() {
        ReflectUtil.ensureLoaded(A.class);
        ReflectUtil.ensureLoaded(B.class);
        final AbstractionProvider abstractionProvider = new AbstractionProvider(AbstractionManager.getInstance());
        abstractionProvider.registerImplsFromResources(
                new PackageWalker(this.getClass(), "test.abstracraft.core")
                .findResources()
                .filter(r -> r.trimmedName().endsWith("Impl"))
        );

        // check impls registered
        Assertions.assertEquals(AImpl.class.getName(), abstractionProvider.getImplByClass(A.class).getName());
        Assertions.assertEquals(BImpl.class.getName(), abstractionProvider.getImplByClass(B.class).getName());
    }

}
