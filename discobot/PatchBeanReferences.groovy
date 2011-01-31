#!/usr/bin/groovy

import groovy.io.FileType
import org.apache.commons.io.FileUtils

filesToPatch = [:]

//Some groovy sources have CRLF line endings, we have to detect it and keep it
//otherwise git will treat it as line changes and we will get confilcts on patching
//a newer version of file using git rebase....

@Grab(group = 'commons-io', module = 'commons-io', version = '1.3.2')
String getLineEndingStyle(File file) {
    file.withReader('utf-8') { reader ->
        char character = reader.read() as char
        while (character != -1 as char && character != '\r' as char) {
            character = reader.read()
        }
        return character == '\r' as char ? '\r\n' : '\n'
    }
}

//parse options
def cli = new CliBuilder(usage: 'PatchBeansReferences.groovy')
cli.with {
    h longOpt: 'help', 'Show usage info'
    p longOpt: 'patch', 'Patch files insead of just listing the files that need to be patched'
    d longOpt: 'dir', args: 1, argName: 'directory', 'Directory in which to look for files to be patched; defaults to ../src/main'
}
def options = cli.parse(args)
if (options.h) {
    cli.usage()
    return
}

new File(options.d ?: '../src/main').eachFileRecurse(FileType.FILES) { file ->
    if (file.name =~ /\.(java|groovy)$/) {
        def matchingLines = file.readLines().grep(~/.*java\.beans\..*/)
        if (matchingLines) {
            filesToPatch[file] = matchingLines
        }
    }
}

def processingClosure
if (!options.p) {
    processingClosure = { file, matchingLines ->
        println "${file.canonicalPath} [found ${matchingLines.size()} matching lines]"
    }
} else {
    processingClosure = { file, matchingLines ->
        println "Patching ${file.canonicalPath} [${matchingLines.size()} line(s)]"
        String eol = getLineEndingStyle(file)
        File temp = File.createTempFile('patch', null)
        temp.withWriter { tempWriter ->
            file.eachLine { line ->
                tempWriter.print(line.replaceAll(~/java\.beans\./, 'org.discobot.beans.') + eol)
            }
        }
        FileUtils.copyFile(temp, file)
    }
}

filesToPatch.each(processingClosure)
