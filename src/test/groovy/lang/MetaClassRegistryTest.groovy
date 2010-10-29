package groovy.lang

/**
 * GROOVY-2875: MetaClassRegistryImpl constantMetaClasses map is leaking resources
 * GROOVY-4481: the listener and iterator mechanism over the MetaClassRegistry wasn't working.
 *
 * @author Jochen Theodorou
 * @author Guillaume Laforge
 */
class MetaClassRegistryTest extends GroovyTestCase {

    def registry = GroovySystem.metaClassRegistry

    void testListenerAdditionAndRemoval() {
        def called = null
        def registry = GroovySystem.metaClassRegistry
        registry.updateConstantMetaClass = { event -> called = event }

        Integer.metaClass.foo = {->}
        assert 1.foo() == null
        assert called != null
        assert registry.metaClassRegistryChangeEventListeners.size() == 2
        registry.removeMetaClassRegistryChangeEventListener(registry.metaClassRegistryChangeEventListeners[1])
        assert registry.metaClassRegistryChangeEventListeners.size() == 1

        def oldCalled = called;
        Integer.metaClass = null

        Integer.metaClass.bar = {}
        assert 1.bar() == null
        shouldFail(MissingMethodException) {
            1.foo()
        }
        assert called == oldCalled

        Integer.metaClass = null
        shouldFail(MissingMethodException) {
            1.bar()
        }
    }

    void testDefaultListenerRemoval() {
        assert registry.metaClassRegistryChangeEventListeners.size() == 1
        registry.removeMetaClassRegistryChangeEventListener(registry.metaClassRegistryChangeEventListeners[0])
        assert registry.metaClassRegistryChangeEventListeners.size() == 1
    }

    void testIteratorIteration() {
        // at the start the iteration might show elements, even if
        // they are no longer in use. After they are added to the list,
        // they can not be collected for now.
        def metaClasses = []
        registry.each { metaClasses << it }

        // we add one more constant meta class and then count them to
        // see if the number fits
        Integer.metaClass.foo = {}

        println metaClasses

        def count = 0;
        registry.each { count++ }
        assert count == 1 + metaClasses.size()

        // we remove the class again, but it might still show up
        // in the list.. so we don't test that
        Integer.metaClass = null
    }

    void _testIteratorRemove() {
        Integer.metaClass.foo {-> 1 }
        assert 1.foo() == 1
        for (def it = registry.iterator(); it.hasNext();) {
            it.remove()
        }
        shouldFail(MissingMethodException) {
            1.foo()
        }
    }

    void testAddingAnEventListenerAndChangingAMetaClassWithAnEMC() {
        def events = []
        def listener = { MetaClassRegistryChangeEvent event ->
            events << event
        } as MetaClassRegistryChangeEventListener

        GroovySystem.metaClassRegistry.addMetaClassRegistryChangeEventListener listener
        String.metaClass.foo = { -> "foo" }

        assert "bar".foo() == "foo"
        assert events.size() == 1
        assert events[0].classToUpdate == String

        GroovySystem.metaClassRegistry.removeMetaClassRegistryChangeEventListener listener
        String.metaClass = null
    }

    void testAddingAnEventListenerAndChangingAMetaClassWithANormalMetaClass() {
        def events = []
        def listener = { MetaClassRegistryChangeEvent event ->
            events << event
        } as MetaClassRegistryChangeEventListener

        GroovySystem.metaClassRegistry.addMetaClassRegistryChangeEventListener listener
        def mc = new MetaClassImpl(Double)
        mc.initialize()
        Double.metaClass = mc

        assert events.size() == 1
        assert events[0].classToUpdate == Double

        GroovySystem.metaClassRegistry.removeMetaClassRegistryChangeEventListener listener
        Double.metaClass = null
    }
}