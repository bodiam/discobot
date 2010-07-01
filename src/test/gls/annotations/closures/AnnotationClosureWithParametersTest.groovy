package gls.annotations.closures

class AnnotationClosureWithParametersTest extends AnnotationClosureExhaustiveTestSupport {
    def getAnnotationClass() { AnnWithClassElement }

    def getAnnotatedClass() { ClosureWithParameters }

    def verify(Object closureClass) {
        def closure = closureClass.newInstance(null, null)
        assert closure.call(1, 2) == 3
    }
}

@AnnWithClassElement(elem = { foo, bar -> foo + bar })
class ClosureWithParameters {
    @AnnWithClassElement(elem = { foo, bar -> foo + bar })
    private aField

    @AnnWithClassElement(elem = { foo, bar -> foo + bar })
    def aProperty

    @AnnWithClassElement(elem = { foo, bar -> foo + bar })
    def aMethod(@AnnWithClassElement(elem = { foo, bar -> foo + bar }) aParam) {
        @AnnWithClassElement(elem = { foo, bar -> foo + bar })
        def aLocal
    }
}