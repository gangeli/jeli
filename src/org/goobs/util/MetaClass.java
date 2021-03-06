package org.goobs.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.HashSet;
import java.util.LinkedList;

public class MetaClass implements Decodable{

    public static class ClassCreationException extends RuntimeException {
		private static final long serialVersionUID = -5980065992461870357L;

		private ClassCreationException() {
			super();
		}

		private ClassCreationException(String msg) {
			super(msg);
		}

		private ClassCreationException(Throwable cause) {
			super(cause);
		}
	}
	
	public static final class ConstructorNotFoundException extends ClassCreationException {
		private static final long serialVersionUID = -5980065992461870357L;

		private ConstructorNotFoundException() {
			super();
		}

		private ConstructorNotFoundException(String msg) {
			super(msg);
		}

		private ConstructorNotFoundException(Throwable cause) {
			super(cause);
		}
	}

	public static final class ClassFactory<Type> {
		private Class<?>[] classParams;
		private Class<Type> cl;
		private Constructor<Type> constructor;

		private boolean samePrimitive(Class<?> a, Class<?> b){
			if(!a.isPrimitive() && !b.isPrimitive()) return false;
			if(a.isPrimitive()){
				try {
					Class<?> type = (Class<?>) b.getField("TYPE").get(null);
					return type.equals(a);
				} catch (Exception e) {
					return false;
				}
			}
			if(b.isPrimitive()){
				try {
					Class<?> type = (Class<?>) a.getField("TYPE").get(null);
					return type.equals(b);
				} catch (Exception e) {
					return false;
				}
			}
			throw new IllegalStateException("Impossible case");
		}
		
		private int superDistance(Class<?> candidate, Class<?> target) {
			if (candidate == null) {
				// --base case: does not implement
				return Integer.MIN_VALUE;
			} else if (candidate.equals(target)) {
				// --base case: exact match
				return 0;
			} else if(samePrimitive(candidate, target)){
				// --base case: primitive and wrapper
				return 0;
			} else {
				// --recursive case: try superclasses
				// case: direct superclass
				Class<?> directSuper = candidate.getSuperclass();
				int superDist = superDistance(directSuper, target);
				if (superDist >= 0)
					return superDist + 1; // case: superclass distance
				// case: implementing interfaces
				Class<?>[] interfaces = candidate.getInterfaces();
				int minDist = Integer.MAX_VALUE;
				for (Class<?> i : interfaces) {
					superDist = superDistance(i, target);
					if (superDist >= 0) {
						minDist = Math.min(minDist, superDist);
					}
				}
				if (minDist != Integer.MAX_VALUE)
					return minDist + 1; // case: interface distance
				else
					return -1; // case: failure
			}
		}

		@SuppressWarnings("unchecked")
		private void construct(String classname, Class<?>... params)
				throws ClassNotFoundException, NoSuchMethodException {
			//--Initialization
			// (save class parameters)
			this.classParams = params;
			// (create class)
			try {
				this.cl = (Class<Type>) Class.forName(classname);
			} catch (ClassCastException e) {
				throw new ClassCreationException("Class " + classname
						+ " could not be cast to the correct type");
			}
			// --Find Constructor
			// (get constructors)
			Constructor<?>[] constructors = cl.getDeclaredConstructors();
			Constructor<?>[] potentials = new Constructor<?>[constructors.length];
			Class<?>[][] constructorParams = new Class<?>[constructors.length][];
			int[] distances = new int[constructors.length]; //distance from base class
			// (filter: length)
			for (int i = 0; i < constructors.length; i++) {
				constructorParams[i] = constructors[i].getParameterTypes();
				if (constructors[i].isVarArgs() && params.length > constructorParams[i].length-1) { // length is good (varargs)
					potentials[i] = constructors[i];
					distances[i] = 0;
				} else if(params.length == constructorParams[i].length){                            // length is good (standard
					potentials[i] = constructors[i];
					distances[i] = 0;
				} else {                                                                            // length is bad
					potentials[i] = null;
					distances[i] = -1;
				}
			}
			// (filter:type)
			for (int paramIndex = 0; paramIndex < params.length; paramIndex++) { // for each parameter...
				Class<?> argParam = params[paramIndex];
				for (int conIndex = 0; conIndex < potentials.length; conIndex++) { // for each constructor...
					if (potentials[conIndex] != null) { // if the constructor is in the pool...
						//(adjust index for varargs)
						int conParamIndex = paramIndex;
						if(paramIndex >= constructorParams[conIndex].length){
							if(potentials[conIndex].isVarArgs()){
								conParamIndex = constructorParams[conIndex].length-1;
							} else {
								throw new IllegalStateException("INTERNAL: parameter lengths don't match");
							}
						}
						//(get class)
						Class<?> conParam = constructorParams[conIndex][conParamIndex];
						if(potentials[conIndex].isVarArgs() && conParamIndex == constructorParams[conIndex].length-1){
							conParam = conParam.getComponentType();
						}
						//(get distance)
						int dist = superDistance(argParam, conParam);
						if (dist >= 0) { // and if the constructor matches...
							distances[conIndex] += dist; // keep it
						} else {
							potentials[conIndex] = null; // else, remove it from the pool
							distances[conIndex] = -1;
						}
					}
				}
			}
			// (filter:min)
			this.constructor = (Constructor<Type>)Utils.argmin(potentials, distances, 0);
			if (this.constructor == null) {
				StringBuilder b = new StringBuilder();
				b.append(classname).append("(");
				for (Class<?> c : params) {
					b.append(c.getName()).append(", ");
				}
				String target = b.substring(0, params.length==0 ? b.length() : b.length() - 2) + ")";
				throw new ConstructorNotFoundException(
						"No constructor found to match: " + target);
			}
		}

		private ClassFactory(String classname, Class<?>... params)
				throws ClassNotFoundException, NoSuchMethodException {
			// (generic construct)
			construct(classname, params);
		}

		private ClassFactory(String classname, Object... params)
				throws ClassNotFoundException, NoSuchMethodException {
			// (convert class parameters)
			Class<?>[] classParams = new Class[params.length];
			for (int i = 0; i < params.length; i++) {
				if(params[i] == null) throw new ClassCreationException("Argument " + i + " to class constructor is null");
				classParams[i] = params[i].getClass();
			}
			// (generic construct)
			construct(classname, classParams);
		}

		private ClassFactory(String classname, String... params)
				throws ClassNotFoundException, NoSuchMethodException {
			// (convert class parameters)
			Class<?>[] classParams = new Class[params.length];
			for (int i = 0; i < params.length; i++) {
				classParams[i] = Class.forName(params[i]);
			}
			// (generic construct)
			construct(classname, classParams);
		}

		private ClassFactory(String classname) throws ClassNotFoundException, NoSuchMethodException {
			construct(classname);
		}

		/**
		 * Creates an instance of the class produced in this factory
		 * 
		 * @param params
		 *            The arguments to the constructor of the class NOTE: the
		 *            resulting instance will [unlike java] invoke the most
		 *            narrow constructor rather than the one which matches the
		 *            signature passed to this function
		 * @return An instance of the class
		 */
		public Type createInstance(Object... params) {
			try {
				//(ensure visibility)
				boolean accessible = true;
				if(!constructor.isAccessible()){
					accessible = false;
					constructor.setAccessible(true);
				}
				//(create arguments)
				Object[] toPass = new Object[constructor.getParameterTypes().length];
				if(params.length < toPass.length){
					throw new IllegalArgumentException("Too few arguments to constructror (" + toPass.length + " but only passed " + params.length + ")");
				}
				System.arraycopy(params,0,toPass,0, toPass.length == 0 ? 0 : toPass.length-1);
				if(constructor.isVarArgs()){
					Class<?> vargsClass = constructor.getParameterTypes()[constructor.getParameterTypes().length-1].getComponentType();
					int length = params.length - (toPass.length - 1);
					Object last = Array.newInstance(vargsClass,length);
					Utils.arraycopy(params,toPass.length-1,last,0,vargsClass,length);
					toPass[toPass.length-1] = last;
				}else if(toPass.length > 0){
					toPass[toPass.length-1] = params[params.length-1];
				}
				Type rtn = constructor.newInstance(toPass);
				if(!accessible){ constructor.setAccessible(false); }
				return rtn;
			} catch (Exception e) {
				throw new ClassCreationException(e);
			}
		}

		/**
		 * Returns the full class name for the objects being produced
		 * 
		 * @return The class name for the objects produced
		 */
		public String getName() {
			return cl.getName();
		}

		@Override
		public String toString() {
			StringBuilder b = new StringBuilder();
			b.append(cl.getName()).append("(");
			for (Class<?> cl : classParams) {
				b.append(" ").append(cl.getName()).append(",");
			}
			b.replace(b.length() - 1, b.length(), " ");
			b.append(")");
			return b.toString();
		}

		@SuppressWarnings({"rawtypes"})
		@Override
		public boolean equals(Object o) {
			if (o instanceof ClassFactory) {
				ClassFactory other = (ClassFactory) o;
				if (!this.cl.equals(other.cl))
					return false;
				for (int i = 0; i < classParams.length; i++) {
					if (!this.classParams[i].equals(other.classParams[i]))
						return false;
				}
				return true;
			} else {
				return false;
			}
		}

		@Override
		public int hashCode() {
			return cl.hashCode();
		}
	}

	private String classname;

	/**
	 * Creates a new MetaClass producing objects of the given type
	 * 
	 * @param classname
	 *            The full classname of the objects to create
	 */
	public MetaClass(String classname) {
		this.classname = classname;
	}

    private MetaClass(){}

	/**
	 * Creates a new MetaClass producing objects of the given type
	 * 
	 * @param classname
	 *            The class to create
	 */
	public MetaClass(Class<?> classname) {
		this.classname = classname.getName();
	}

	/**
	 * Creates a factory for producing instances of this class from a
	 * constructor taking no types
	 *
	 * @param <E>
	 *            The type of the objects to be produced
	 * @return A ClassFactory of the given type
	 */
	public <E> ClassFactory<E> createFactory() {
		try {
			return new ClassFactory<E>(classname);
		} catch (ClassCreationException e) {
			throw e;
		} catch (Exception e) {
			throw new ClassCreationException(e);
		}
	}

	/**
	 * Creates a factory for producing instances of this class from a
	 * constructor taking the given types as arguments
	 * 
	 * @param <E>
	 *            The type of the objects to be produced
	 * @param classes
	 *            The types used in the constructor
	 * @return A ClassFactory of the given type
	 */
	public <E> ClassFactory<E> createFactory(Class<?>... classes) {
		try {
			return new ClassFactory<E>(classname, classes);
		} catch (ClassCreationException e){
			throw e;
		} catch (Exception e) {
			throw new ClassCreationException(e);
		}
	}

	/**
	 * Creates a factory for producing instances of this class from a
	 * constructor taking the given types as arguments
	 * 
	 * @param <E>
	 *            The type of the objects to be produced
	 * @param classes
	 *            The types used in the constructor
	 * @return A ClassFactory of the given type
	 */
	public <E> ClassFactory<E> createFactory(String... classes) {
		try {
			return new ClassFactory<E>(classname, classes);
		} catch (ClassCreationException e){
			throw e;
		} catch (Exception e) {
			throw new ClassCreationException(e);
		}
	}

	/**
	 * Creates a factory for producing instances of this class from a
	 * constructor taking objects of the types given
	 * 
	 * @param <E>
	 *            The type of the objects to be produced
	 * @param objects
	 *            Instances of the types used in the constructor
	 * @return A ClassFactory of the given type
	 */
	public <E> ClassFactory<E> createFactory(Object... objects) {
		try {
			return new ClassFactory<E>(classname, objects);
		} catch (ClassCreationException e){
			throw e;
		} catch (Exception e) {
			throw new ClassCreationException(e);
		}
	}

	/**
	 * Create an instance of the class, inferring the type automatically, and
	 * given an array of objects as constructor parameters NOTE: the resulting
	 * instance will [unlike java] invoke the most narrow constructor rather
	 * than the one which matches the signature passed to this function
	 * 
	 * @param <E>
	 *            The type of the object returned
	 * @param objects
	 *            The arguments to the constructor of the class
	 * @return An instance of the class
	 */
	public <E> E createInstance(Object... objects) {
		ClassFactory<E> fact = createFactory(objects);
		return fact.createInstance(objects);
	}

	/**
	 * Creates an instance of the class, forcing a cast to a certain type and
	 * given an array of objects as constructor parameters NOTE: the resulting
	 * instance will [unlike java] invoke the most narrow constructor rather
	 * than the one which matches the signature passed to this function
	 * 
	 * @param <E>
	 *            The type of the object returned
	 * @param type
	 *            The class of the object returned
	 * @param params
	 *            The arguments to the constructor of the class
	 * @return An instance of the class
	 */
	@SuppressWarnings("unchecked")
	public <E,F extends E> F createInstance(Class<E> type, Object... params) {
		Object obj = createInstance(params);
		if (type.isInstance(obj)) {
			return (F) obj;
		} else {
			throw new ClassCreationException("Cannot cast " + classname
					+ " into " + type.getName());
		}
	}
	
	public boolean checkConstructor(Object... params){
		try{
			createInstance(params);
			return true;
		} catch(ConstructorNotFoundException e){
			return false;
		}
	}

    @Override
    public Decodable decode(String encoded, Type[] typeParams) {
        this.classname = encoded;
        return this;
    }

    @Override
    public String encode() {
        return this.classname;
    }

	@Override
	public String toString() {
		return classname;
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof MetaClass) {
			return ((MetaClass) o).classname.equals(this.classname);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return classname.hashCode();
	}

	/**
	 * Creates a new MetaClass (helper method)
	 * 
	 * @param classname
	 *            The name of the class to create
	 * @return A new MetaClass object of the given class
	 */
	public static MetaClass create(String classname) {
		return new MetaClass(classname);
	}
	
	/**
	 * Creates a new MetaClass (helper method)
	 * 
	 * @param clazz
	 *            The class to create
	 * @return A new MetaClass object of the given class
	 */
	public static MetaClass create(Class <?> clazz) {
		return new MetaClass(clazz);
	}

	/**
	 * Get all fields defined by this class. If a field is overwritten,
	 * we return only the most specific field -- i.e., the one that is accessible.
	 * @param c The class to get fields from
	 * @return The fields in this an all super classes
	 */
	public static Field[] getFields(Class c){
		HashSet<String> visibleNames = new HashSet<String>();
		LinkedList<Field> fields = new LinkedList<Field>();
		//(while is a class)
		while(c != null){
			//(for each declared field)
			for(Field f : c.getDeclaredFields()){
				//(if private or isn't overwritten)
				if(Modifier.isPrivate(f.getModifiers()) || !visibleNames.contains(f.getName())){
					///(add it)
					visibleNames.add(f.getName());
					fields.add(f);
				}
			}
			//(proceed to superclass)
			c = c.getSuperclass();
		}
		return fields.toArray(new Field[fields.size()]);
	}

	public static <E> Field findField(Class<E> clazz, String field) throws NoSuchFieldException {
		try {
			return clazz.getField(field);
		} catch (NoSuchFieldException e) {
			Field[] fields = getFields(clazz);
			for(Field f : fields){
				if(f.getName().equals(field)) {
					return f;
				}
			}
			throw new NoSuchFieldException(field + " in " + clazz);
		}
	}

	@SuppressWarnings({"unchecked"})
	public static <E extends Annotation> E findAnnotation(Class clazz, Class<E> ann) {
		if(clazz == null){ return null; }
		E cand = (E) clazz.getAnnotation(ann);
		if(cand == null){
			return findAnnotation(clazz.getSuperclass(), ann);
		} else {
			return cand;
		}
	}

}
