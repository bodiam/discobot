package groovy.bugs

class POJOCallSiteBug extends GroovyTestCase {

    MetaClassRegistry registry
    MetaClass originalMetaClass

    void setUp() {
        registry = GroovySystem.metaClassRegistry
        originalMetaClass = registry.getMetaClass(POJOCallSiteBugFoo)
    }

    void tearDown() {
        registry.setMetaClass(POJOCallSiteBugFoo, originalMetaClass)
    }

    void testPOJOCallSiteShouldBeUpdatedAfterMetaClassIsChanged() {
        def foo = {s -> s.foo() }
        def s = new POJOCallSiteBugFoo()

        POJOCallSiteBugFoo.metaClass = new POJOCallSiteBugProxyMetaClass(registry, POJOCallSiteBugFoo, originalMetaClass, 'foo')
        assert 'foo' == s.foo()
        assert 'foo' == foo(s)

        POJOCallSiteBugFoo.metaClass = new POJOCallSiteBugProxyMetaClass(registry, POJOCallSiteBugFoo, originalMetaClass, 'test')
        assert 'test' == s.foo()
        assert 'test' == foo(s)
    }

    void testPOJOPropertyCallSiteShouldBeUpdatedAfterMetaClassIsChanged() {
        def bar = {foo -> foo.bar }
        def foo = new POJOCallSiteBugFoo()

        assert 'bar' == foo.bar
        assert 'bar' == bar(foo)

        foo.metaClass = new POJOCallSiteBugProxyMetaClass(registry, POJOCallSiteBugFoo, originalMetaClass, 'test')

        assert 'test' == foo.bar
        assert 'test' == bar(foo)
    }

    void testPOJOFieldCallSiteShouldBeUpdatedAfterMetaClassIsChanged() {
        def field = {foo -> foo.field }
        def foo = new POJOCallSiteBugFoo()

        assert 'field' == foo.field
        assert 'field' == field(foo)

        foo.metaClass = new POJOCallSiteBugProxyMetaClass(registry, POJOCallSiteBugFoo, originalMetaClass, 'test')

        assert 'test' == foo.field
        assert 'test' == field(foo)
    }

}

class POJOCallSiteBugProxyMetaClass extends ProxyMetaClass {
    String result

    POJOCallSiteBugProxyMetaClass(MetaClassRegistry metaClassRegistry, Class aClass, MetaClass adaptee, String result) {
        super(metaClassRegistry, aClass, adaptee)
        this.result = result
    }

    public Object invokeMethod(final Object object, final String methodName, final Object[] arguments) {
        result
    }

    public Object getProperty(Class aClass, Object object, String property, boolean b, boolean b1) {
        result
    }
}