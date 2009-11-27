package groovy

class StaticImportTarget {
    def static x(String message, int times) {
        return message * times
    }
    def y(String message, int times) {
        return x(message, times)
    }
    static z() {
        assert false, "this.z()/super.z() was resolved to statically imported method"
    }
}
