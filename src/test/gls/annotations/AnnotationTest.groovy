/*
 * Copyright 2003-2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package gls.annotations

import gls.CompilableTestSupport

/**
 * Tests various properties of annotation definitions.
 *
 * @author Jochen Theodorou
 * @author Guillaume Laforge
 */
class AnnotationTest extends CompilableTestSupport {

    /**
     * Check that it is possible to annotate an annotation definition with field and method target elements.
     */
    void testAnnotateAnnotationDefinitionWithMethodAndFieldTargetElementTypes() {
        shouldCompile """
            import java.lang.annotation.*
            import static java.lang.annotation.RetentionPolicy.*
            import static java.lang.annotation.ElementType.*

            @Retention(RUNTIME)
            @Target([METHOD, FIELD])
            @interface MyAnnotation { }
        """
    }

    void testCannotAnnotateAnotationDefinitionIfTargetIsNotOfType() {
        shouldNotCompile """
            import java.lang.annotation.*
            import static java.lang.annotation.ElementType.*

            // all target elements except ANNOTATION_TYPE
            @Target([CONSTRUCTOR, METHOD, FIELD, LOCAL_VARIABLE, PACKAGE, PARAMETER, TYPE])
            @interface MyAnnotation { }

            @MyAnnotation
            @interface AnotherAnnotation {}
        """
    }

    /**
     * The @OneToMany cascade parameter takes an array of CascadeType.
     * To use this annotation in Java with this parameter, you do <code>@OneToMany(cascade = { CascadeType.ALL })</code>
     * In Groovy, you do <code>@OneToMany(cascade = [ CascadeType.ALL ])</code> (brackets instead of braces)
     * But when there's just one value in the array, the curly braces or brackets can be omitted:
     * <code>@OneToMany(cascade = [ CascadeType.ALL ])</code>
     */
    void testOmittingBracketsForSingleValueArrayParameter() {
        shouldCompile """
            import gls.annotations.*

            class Book {}

            class Author {
                @OneToMany(cascade = CascadeType.ALL)
                Set<Book> books
            }

            def annotation = Author.class.getDeclaredField('books').annotations[0]

            assert annotation instanceof OneToMany
            assert annotation.cascade() == [CascadeType.ALL]
        """
    }

  void testPrimitiveDefault() {
    // NOTE: for int anything else than a plain number will fail.
    // For example 1l will fail too, even if it could be converted.
    // If this should be changed, then further discussion is needed.
    shouldNotCompile """
@interface X {
  int x() default "1" // must be integer
}
    """

    shouldCompile """
@interface X {
  int x() default 1
}
    """
  }

  void testConstant() {
    assertScript """
    class Baz {
        // static final int OTHER = 5
        // below we would like to but can't use:
        // constant field expressions, e.g. OTHER, or
        // constant property expressions, e.g. Short.MAX_VALUE
        @Foo(5) void run() {
            assert Baz.class.getMethod('run').annotations[0].value() == 5
        }
    }

    import java.lang.annotation.*
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Foo {
        int value() default -3
    }

    new Baz().run()
    """
  }

  void testArrayDefault() {
    shouldNotCompile """
@interface X {
   String[] x() default "1" // must be list
}
    """

    shouldNotCompile """
@interface X {
   String[] x() default ["1",2] // list must contain elements of correct type
}
    """

    shouldCompile """
@interface X {
   String[] x() default ["a","b"]
}
    """
  }

  void testClassDefault() {
    shouldNotCompile """
@interface X {
   Class x() default "1" // must be list
}
    """

    shouldCompile """
@interface X {
   Class x() default Number.class // class with .class
}
    """

    shouldCompile """
@interface X {
   Class x() default Number
}
    """
  }

  void testEnumDefault() {
    shouldNotCompile """
@interface X {
   ElementType x() default "1" // must be Enum
}
    """

    shouldNotCompile """
@interface X {
   ElementType x() default Target.TYPE // must be Enum of correct type
}
    """

    shouldCompile """
import java.lang.annotation.*

@interface X {
   ElementType x() default ElementType.METHOD
}
    """
  }

  void testAnnotationDefault() {
    shouldNotCompile """
import java.lang.annotation.*
@interface X {
   Target x() default "1" // must be Annotation
}
    """

    shouldNotCompile """
import java.lang.annotation.*

@interface X {
   Target x() default @Retentention // must be correct type
}
    """

    shouldCompile """
import java.lang.annotation.*
@interface X {
   Target x() default @Target([ElementType.TYPE])
}
    """
  }

  void testSelfReference() {
    shouldNotCompile """
@interface X {
   X x() default @X // self reference must not work
}
    """
  }

  void testCyclicReference() {
    shouldNotCompile """
@interface X {
   Y x() default @Y
}

@interface Y {
   X x() default @X
}
    """
  }

  void testParameter() {
    shouldNotCompile """
@interface X {
   String x(int x) default "" // annotation members can't have parameters
}
    """
  }

  void testThrowsClause() {
    shouldNotCompile """
@interface X {
   String x() throws IOException // annotation members can't have exceptions
}
    """
  }

  void testExtends() {
    shouldNotCompile """
@interface X extends Serializable{}
// annotation members can't extend
    """
  }

  void testInvalidMemberType() {
    shouldNotCompile """
@interface X {
   Object x() // Object is no type for constants
}
    """
  }

  void testNull() {
    shouldNotCompile """
@interface X {
   String x() default null // null is no constant for annotations
}
    """
  }

  void testUsage() {
    assertScript """
import java.lang.annotation.*

// a random annnotation type
@Retention(RetentionPolicy.RUNTIME)
@interface MyAnnotation {
  String stringValue()
  int intValue()
  int defaultInt() default 1
  String defaultString() default ""
  Class defaultClass() default Integer.class
  ElementType defaultEnum() default ElementType.TYPE
  Target defaultAnnotation() default @Target([ElementType.TYPE])
}


@MyAnnotation(stringValue = "for class", intValue = 100)
class Foo {}

Annotation[] annotations = Foo.class.annotations
assert annotations.size() == 1 + 1 // + 1 for implicit @GroovyCompilerVersion
MyAnnotation my = Foo.class.getAnnotation(MyAnnotation)
assert my.stringValue() == "for class"
assert my.intValue() == 100
assert my.defaultInt() == 1
assert my.defaultString() == ""
assert my.defaultClass() == Integer

assert my.defaultEnum() == ElementType.TYPE
assert my.defaultAnnotation() instanceof Target
    """
  }

  void testJavaAnnotationUsageWithGroovyKeyword() {
    assertScript """
package gls.annotations
import java.lang.annotation.*
@JavaAnnotation(in = 3)
class Foo {}

Annotation[] annotations = Foo.class.annotations
assert annotations.size() == 1 + 1// + 1 for implicit @GroovyCompilerVersion
JavaAnnotation my = Foo.class.getAnnotation(JavaAnnotation)
assert my.in() == 3
    """
  }

  void testUsageOnClass() {
    assertScript """
import java.lang.annotation.*

@Deprecated
class Foo{}

assert Foo.class.annotations.size() == 1 + 1 // + 1 for implicit @GroovyCompilerVersion
  """
  }

  void testSingletonArrayUsage() {
    assertScript """
import java.lang.annotation.*

// a random annnotation type
@Retention(RetentionPolicy.RUNTIME)
@interface MyAnnotation {
  String[] things()
}

@MyAnnotation(things = "x")
class Foo {}

Annotation[] annotations = Foo.class.annotations
assert annotations.size() == 1 + 1 // + 1 for implicit @GroovyCompilerVersion
MyAnnotation my = Foo.class.getAnnotation(MyAnnotation)
assert my.things().size() == 1
assert my.things()[0] == "x"
    """
  }

  void testSettingAnnotationMemberTwice() {
    shouldNotCompile """
package gls.annotations

@JavaAnnotation(in = 1, in = 2) 
class Foo {}
    """
  }

  void testGetterCallWithSingletonAnnotation() {
      assertScript """
          @Singleton class MyService3410{}
          assert MyService3410.instance != null     
          assert MyService3410.getInstance() != null
      """
      println "testGetterCallWithSingletonAnnotation Done" 
  }

    void testAttributePropertyConstants() {
        assertScript """
        import java.lang.annotation.*
        import static Constants.*

        @Retention(RetentionPolicy.RUNTIME)
        @Target(ElementType.FIELD)
        @interface Anno {
            double value() default 0.0d
            String[] array() default []
        }

        class Constants {
            public static final String BAR = "bar"
            public static final APPROX_PI = 3.14d
        }

        interface IConstants {
            String FOO = "foo"
        }

        class ClassWithAnnotationUsingConstant {
            @Anno(array = [IConstants.FOO, BAR, groovy.inspect.Inspector.GROOVY])
            public annotatedStrings

            @Anno(Math.PI)
            public annotatedMath1
            @Anno(APPROX_PI)
            public annotatedMath2
        }

        assert ClassWithAnnotationUsingConstant.getDeclaredField('annotatedStrings').annotations[0].array() == ['foo', 'bar', "GROOVY"]
        assert ClassWithAnnotationUsingConstant.getDeclaredField('annotatedMath1').annotations[0].value() == Math.PI
        assert ClassWithAnnotationUsingConstant.getDeclaredField('annotatedMath2').annotations[0].value() == Constants.APPROX_PI
      """
    }

    void testNestedAttributePropertyConstants() {
        assertScript """
        import java.lang.annotation.*
        import static Constants.*

        @Retention(RetentionPolicy.RUNTIME)
        @Target(ElementType.FIELD)
        @interface Outer {
            Inner value()
            String[] array() default []
        }

        @Retention(RetentionPolicy.RUNTIME)
        @Target(ElementType.FIELD)
        @interface Inner {
            String[] value() default []
        }

        class Constants {
            public static final String BAR = "bar"
            public static final String BAZ = "baz"
        }

        interface IConstants {
            String FOO = "foo"
            String FOOBAR = "foobar"
        }

        class ClassWithNestedAnnotationUsingConstant {
            @Outer(value = @Inner([IConstants.FOOBAR, BAR, groovy.inspect.Inspector.GROOVY]),
                   array = [IConstants.FOO, groovy.inspect.Inspector.GROOVY, BAZ])
            public outer
        }

        assert ClassWithNestedAnnotationUsingConstant.getDeclaredField('outer').annotations[0].array() == ['foo', 'GROOVY', 'baz']
        assert ClassWithNestedAnnotationUsingConstant.getDeclaredField('outer').annotations[0].value().value() == ['foobar', 'bar', 'GROOVY']
      """
    }

    void testRuntimeRetentionAtAllLevels() {
        assertScript """
        import java.lang.annotation.*

        @Retention(RetentionPolicy.RUNTIME)
        @Target([ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER, ElementType.CONSTRUCTOR, ElementType.FIELD])
        @interface MyAnnotation {
            String value() default ""
        }

        @MyAnnotation('class')
        class MyClass {
            @MyAnnotation('field')
            public myField

            @MyAnnotation('constructor')
            MyClass(@MyAnnotation('constructor param') arg){}

            @MyAnnotation('method')
            def myMethod(@MyAnnotation('method param') arg) {}
        }

        def c1 = new MyClass()
        assert c1.class.getAnnotation(MyAnnotation).value() == 'class'

        def field = c1.class.fields.find{ it.name == 'myField' }
        assert field.annotations[0].value() == 'field'

        def method = c1.class.methods.find{ it.name == 'myMethod' }
        assert method.annotations[0].value() == 'method'
        assert method.parameterAnnotations[0][0].value() == 'method param'

        def constructor = c1.class.constructors[0]
        assert constructor.annotations[0].value() == 'constructor'
        assert constructor.parameterAnnotations[0][0].value() == 'constructor param'
        """
    }

    void testAnnotationWithValuesNotGivenForAttributesWithoutDefaults() {
      def scriptStr
      scriptStr = """
        import java.lang.annotation.Retention
        import java.lang.annotation.RetentionPolicy
        
        @Retention(RetentionPolicy.RUNTIME)
        @interface Annot3454V1 {
          String x()
        }
        
        @Annot3454V1
        class Bar {}
      """
      // compiler should not allow this, because there is no default value for x
      shouldNotCompile(scriptStr)
      
      scriptStr = """
            import java.lang.annotation.Retention
            import java.lang.annotation.RetentionPolicy
            
            @Retention(RetentionPolicy.RUNTIME)
            @interface Annot3454V2 {
              String x() default 'xxx'
              String y()
            }
            
            @Annot3454V2
            class Bar {}
        """
        // compiler should not allow this, because there is no default value for y
        shouldNotCompile(scriptStr)
      
        scriptStr = """
            import java.lang.annotation.Retention
            import java.lang.annotation.RetentionPolicy
            
            @Retention(RetentionPolicy.RUNTIME)
            @interface Annot3454V3 {
              String x() default 'xxx'
              String y()
            }
            
            @Annot3454V3(y = 'yyy')
            class Bar {}
            
            def anno = Bar.class.getAnnotation(Annot3454V3)
            assert anno.x() == 'xxx'
            assert anno.y() == 'yyy'
        """
        // compiler should allow this, because there is default value for x and value provided for y
        assertScript(scriptStr)
    }

    void testAllowedTargetsCheckAlsoWorksForAnnotationTypesDefinedOutsideThisCompilationUnit() {
        shouldCompile """
@java.lang.annotation.Documented
@interface Foo {}
        """

        shouldNotCompile """
@java.lang.annotation.Documented
class Foo {
    def bar
}
        """

        shouldNotCompile """
class Foo {
    @java.lang.annotation.Documented
    def bar
}
        """

        shouldCompile """
import org.junit.*

class Foo {
    @Test
    void foo() {}
}
        """

        shouldNotCompile """
import org.junit.*

@Test
class Foo {
    void foo() {}
}
        """
    }
}
