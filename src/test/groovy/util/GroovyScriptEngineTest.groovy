/*
 * Copyright 2003-2010 the original author or authors.
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
package groovy.util

import org.codehaus.groovy.control.CompilerConfiguration;

/**
 * @author Chuck Tassoni
 */
class GroovyScriptEngineTest extends GroovyTestCase {

    private File currentDir
    private File srcDir;
    private File script
    private File com
    private File company
    private File util
    private File makeMeSuper
    private File makeMe
    private File helperIntf
    private File helper
    private File bug4013
    private File bug4234

    private List allFiles = [currentDir, srcDir, script, company, util, makeMeSuper, makeMe, helperIntf, helper, bug4013, bug4234]

    /**
     * Here we have inheritance and delegation-- where the delegate implements an
     * interface-- all used by a dynamically instantiated class named 'MakeMe'.
     */
    public void setUp() {
        locateCurrentDir();
        srcDir = new File(currentDir, 'dynamicSrcRootToBeDeleted')
        srcDir.mkdir();

        script = new File(srcDir, 'script.groovy')
        script.delete()
        script << """
            def obj = dynaInstantiate.instantiate(className, getClass().getClassLoader())
            obj.modifyWidth(dim, addThis)
            returnedMessage = obj.message
        """

        com = new File(srcDir, 'com')
        com.mkdir()
        company = new File(com, 'company')
        company.mkdir()

        makeMeSuper = new File(company, "MakeMeSuper.groovy")
        makeMeSuper.delete()
        makeMeSuper << """
            package com.company
            import com.company.util.*
            class MakeMeSuper{
               private HelperIntf helper = new Helper()
               def getMessage(){
                       helper.getMessage()
               }
            }    
         """

        makeMe = new File(company, "MakeMe.groovy")
        makeMe.delete()
        makeMe << """
            package com.company

            class MakeMe extends MakeMeSuper{
               def modifyWidth(dim, addThis){
                  dim.width += addThis
               }
            }    
         """

        util = new File(company, 'util')
        util.mkdir()

        helperIntf = new File(util, "HelperIntf.groovy")
        helperIntf.delete()
        helperIntf << """
            package com.company.util
            interface HelperIntf{
               public String getMessage();
            }    
         """

        helper = new File(util, "Helper.groovy")
        helper.delete()
        helper << """
            package com.company.util
            class Helper implements HelperIntf{
               public String getMessage(){
                     'worked'
               }
            }    
         """

        bug4013 = new File(srcDir, "Groovy4013Helper.groovy")
        bug4013.delete()
        bug4013 << """
            import java.awt.event.*
            import java.awt.*
            
            class Groovy4013Helper
            {
               def initPanel()
               {
                    def b = new Button('click me')
                    b.addActionListener( new ActionListener(){
                        public void actionPerformed(ActionEvent e) {}
                    })
               }
            }
         """

        bug4234 = new File(srcDir, "Groovy4234Helper.groovy")
        bug4234.delete()
        bug4234 << """
            class Foo4234 {
                static main(args){
                    println "Running Foo4234 -> main()"
                }
            }
            
            class Bar4234 { }
         """
    }

    public void tearDown() {
        try {
            allFiles*.delete()
        } catch (Exception ex) {
            throw new RuntimeException("Could not delete entire dynamic tree inside " + currentDir, ex)
        }
    }

    public void testDynamicInstantiation() throws Exception {
        //Code run in the script will modify this dimension object.
        MyDimension dim = new MyDimension();

        String[] roots = new String[1]
        roots[0] = srcDir.getAbsolutePath()
        GroovyScriptEngine gse = new GroovyScriptEngine(roots);
        Binding binding = new Binding();
        binding.setVariable("dim", dim);
        binding.setVariable("dynaInstantiate", this);

        binding.setVariable("className", "com.company.MakeMe");

        int addThis = 3;
        binding.setVariable("addThis", addThis);

        gse.run("script.groovy", binding);

        //The script instantiated com.company.MakeMe via our own
        //instantiate method.  The instantiated object modified the
        //width of our Dimension object, adding the value of our
        //'addThis' variable to it.
        assertEquals(new MyDimension(addThis, 0), dim);

        assertEquals('worked', binding.getVariable("returnedMessage"))
    }

    /**
     * Test for GROOVY-3281, to ensure details passed through CompilerConfiguration are inherited by GSE.
     */
    void testCompilerConfigurationInheritance() {
        CompilerConfiguration cc = new CompilerConfiguration();
        cc.scriptBaseClass = CustomBaseClass.name

        GroovyClassLoader cl = new GroovyClassLoader(this.class.getClassLoader(), cc)
        GroovyScriptEngine engine = new GroovyScriptEngine("src/test/groovy/util", cl)
        def aScript = engine.createScript("groovyScriptEngineSampleScript.groovy", new Binding())

        assert aScript instanceof CustomBaseClass
    }

    /** GROOVY-3893 */
    void testGSEWithNoScriptRoots() {
        shouldFail ResourceException, {
            String[] emptyScriptRoots = []
            GroovyScriptEngine gse = new GroovyScriptEngine(emptyScriptRoots)
            gse.run("unknownScriptName", "")
        }
    }

    /** GROOVY-4013 */
    void testGSENoCachingOfInnerClasses() {
        def klazz, gse

        String[] roots = new String[1]
        roots[0] = srcDir.getAbsolutePath()

        gse = new GroovyScriptEngine(roots)

        klazz = gse.loadScriptByName('Groovy4013Helper.groovy')
        assert klazz.name == 'Groovy4013Helper'

        klazz = gse.loadScriptByName('Groovy4013Helper.groovy')
        assert klazz.name == 'Groovy4013Helper' // we should still get the outer class, not inner one
    }

    /** GROOVY-4234 */
    void testGSERunningAScriptThatHasMultipleClasses() {
        def klazz, gse

        String[] roots = new String[1]
        roots[0] = srcDir.getAbsolutePath()

        gse = new GroovyScriptEngine(roots)

        println "testGSELoadingAScriptThatHasMultipleClasses - Run 1"
        gse.run("Groovy4234Helper.groovy", new Binding())

        println "testGSELoadingAScriptThatHasMultipleClasses - Run 2"
        gse.run("Groovy4234Helper.groovy", new Binding())
    }

    /** GROOVY-2811 and GROOVY-4286  */
    void testReloadingInterval() {
        def f = File.createTempFile("gse", ".groovy", new File("./target"))
        try {
            def scriptName = f.name

            def gse = new GroovyScriptEngine(f.parentFile.name)
            gse.config.minimumRecompilationInterval = 500

            def binding = new Binding([:])

            f << "1\n"
            sleep 200
            // first time, the script is compiled and cached
            assert gse.run(scriptName, binding) == 1

            f.delete()
            f << "2\n"
            sleep 1000
            // the file was updated, and we waited for more than the minRecompilationInterval
            assert gse.run(scriptName, binding) == 2

            f.delete()
            f << "3\n"
            sleep 100
            // still the old result, as we didn't wait more than the minRecompilationInterval
            assert gse.run(scriptName, binding) == 2

            sleep 1000
            // we've waited enough, so we get the new output
            assert gse.run(scriptName, binding) == 3
        } finally {
            f.delete()
        }
    }

    /**
     * The script passes the className of the class it's supposed to
     * instantiate to this method, expecting a newly instantiated object
     * in return.  The reason this is not done in the script is that
     * we want to ensure that no unforeseen problems occur if
     * the instantiation is not actually done inside the script,
     * since real-world usages will likely require delegating that
     * job.
     */
    public Object instantiate(String className, ClassLoader classLoader) {
        Class clazz = null;
        try {
            clazz = Class.forName(className, true, classLoader);
        } catch (ClassNotFoundException ex) {
            throw new RuntimeException("Class.forName failed for  " + className, ex);
        }
        try {
            return clazz.newInstance();
        } catch (Exception ex) {
            throw new RuntimeException("Could not instantiate object of class " + className, ex);
        }

    }

    private void locateCurrentDir() {
        String bogusFile = "bogusFile";
        File f = new File(bogusFile);
        String path = f.getAbsolutePath();
        path = path.substring(0, path.length() - bogusFile.length());
        currentDir = new File(path);
    }

}

class MyDimension {
    int width
    int height

    MyDimension(int x, int y) {
        width = x
        height = y
    }

    MyDimension() {
        width = 0
        height = 0
    }

    boolean equals(o) { o.width == width && o.height == height }

    int hashCode() { width + 13 * height }
}

abstract class CustomBaseClass extends Script {}
