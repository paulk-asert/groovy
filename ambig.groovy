def method(Map m) { println m.keySet().join(',') }

def method(List l) { println l.join(',') }

//def method(org.codehaus.groovy.runtime.NullObject no) { println 'nothing to see here' }

method(null)
/*
Exception thrown

groovy.lang.GroovyRuntimeException: Ambiguous method overloading for method ConsoleScript3#method.
Cannot resolve which method to invoke for [Ljava.lang.Class;@2bf7e2d4 due to overlapping prototypes between:
	[Lorg.codehaus.groovy.reflection.CachedClass;@bf7ee5b
	[Lorg.codehaus.groovy.reflection.CachedClass;@5f399ef7
*/
