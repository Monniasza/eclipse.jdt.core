/*******************************************************************************
 * Copyright (c) 2016, 2017 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * This is an implementation of an early-draft specification developed under the Java
 * Community Process (JCP) and is made available for testing and evaluation purposes
 * only. The code is not compatible with any specification of the JCP.
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *
 *******************************************************************************/
package org.eclipse.jdt.internal.compiler.lookup;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.internal.compiler.env.IModuleAwareNameEnvironment;
import org.eclipse.jdt.internal.compiler.env.IModuleContext;
import org.eclipse.jdt.internal.compiler.env.IModuleEnvironment;
import org.eclipse.jdt.internal.compiler.env.IModule;
import org.eclipse.jdt.internal.compiler.env.IModule.IModuleReference;
import org.eclipse.jdt.internal.compiler.env.IModule.IPackageExport;
import org.eclipse.jdt.internal.compiler.env.IModule.IService;
import org.eclipse.jdt.internal.compiler.env.INameEnvironment;
import org.eclipse.jdt.internal.compiler.util.HashtableOfPackage;
import org.eclipse.jdt.internal.compiler.util.JRTUtil;

public class ModuleBinding extends Binding {

	public static class UnNamedModule extends ModuleBinding {

		UnNamedModule(LookupEnvironment env) {
			super(env);
		}
		public ModuleBinding[] getAllRequiredModules() {
			return NO_REQUIRES;
		}
		public IModuleContext getModuleLookupContext() {
			return IModuleContext.UNNAMED_MODULE_CONTEXT;
		}
		public IModuleContext getDependencyClosureContext() {
			return IModuleContext.UNNAMED_MODULE_CONTEXT;
		}
		public IModuleContext getModuleGraphContext() {
			return IModuleContext.UNNAMED_MODULE_CONTEXT;
		}
		public boolean canSee(PackageBinding pkg) {
			//TODO - if the package is part of a named module, then we should check if the module exports the package
			return true;
		}
	}
	public static class Service {
		public TypeBinding service;
		public TypeBinding[] implementations;
	}
	public boolean isOpen;
	public char[] moduleName;
	public IModuleReference[] requires;
	public IPackageExport[] exports;
	public IPackageExport[] opens;
	public TypeBinding[] uses;
	Service[] services;
	public CompilationUnitScope scope;
	public LookupEnvironment environment;
	public int tagBits;
	private ModuleBinding[] requiredModules = null;
	private boolean isAuto;
	private char[][] iUses = null;
	private IService[] iServices = null;
	HashtableOfPackage declaredPackages;
	HashtableOfPackage exportedPackages;

	public static ModuleBinding[] NO_REQUIRES = new ModuleBinding[0];

	ModuleBinding(LookupEnvironment env) {
		this.moduleName = ModuleEnvironment.UNNAMED;
		this.environment = env;
		this.requires = IModule.NO_MODULE_REFS;
		this.exports = IModule.NO_EXPORTS;
		this.exports = IModule.NO_OPENS;
		this.services = new Service[0];
		this.declaredPackages = new HashtableOfPackage(0);
		this.exportedPackages = new HashtableOfPackage(0);
	}
	public ModuleBinding(IModule module, LookupEnvironment environment) {
		this.isOpen = module.isOpen();
		this.moduleName = module.name();
		IModule decl = module;
		this.requires = decl.requires();
		if (this.requires == null)
			this.requires = IModule.NO_MODULE_REFS;
		this.exports = decl.exports();
		if (this.exports == null)
			this.exports = IModule.NO_EXPORTS;
		this.opens = decl.opens();
		if (this.opens == null)
			this.opens = IModule.NO_OPENS;
		this.environment = environment;
		this.uses = Binding.NO_TYPES;
		this.services = new Service[0];
		this.iUses = decl.uses();
		this.iServices = decl.provides();
		this.declaredPackages = new HashtableOfPackage(5);
		this.exportedPackages = new HashtableOfPackage(5);
		this.isAuto = module.isAutomatic();
	}

	private Stream<ModuleBinding> getRequiredModules(boolean transitiveOnly) {
		return Stream.of(this.requires).filter(ref -> transitiveOnly ? ref.isTransitive() : true)
			.map(ref -> this.environment.getModule(ref.name()))
			.filter(mod -> mod != null);
	}
	private void collectAllDependencies(Set<ModuleBinding> deps) {
		getRequiredModules(false).forEach(m -> {
			if (deps.add(m)) {
				m.collectAllDependencies(deps);
			}
		});
	}
	private void collectTransitiveDependencies(Set<ModuleBinding> deps) {
		getRequiredModules(true).forEach(m -> {
			if (deps.add(m)) {
				m.collectTransitiveDependencies(deps);
			}
		});
	}

	// All modules required by this module, either directly or indirectly
	public Supplier<Collection<ModuleBinding>> dependencyGraphCollector() {
		return () -> getRequiredModules(false)
			.collect(HashSet::new,
				(set, mod) -> {
					set.add(mod);
					mod.collectAllDependencies(set);
				},
				HashSet::addAll);
	}
	// All direct and transitive dependencies of this module
	public Supplier<Collection<ModuleBinding>> dependencyCollector() {
		return () -> getRequiredModules(false)
			.collect(HashSet::new,
				(set, mod) -> {
					set.add(mod);
					mod.collectTransitiveDependencies(set);
				},
				HashSet::addAll);
	}

	/**
	 * Get all the modules required by this module
	 * All required modules include modules explicitly specified as required in the module declaration
	 * as well as implicit dependencies - those specified as ' requires public ' by one of the
	 * dependencies
	 * 
	 * @return
	 *   An array of all required modules
	 */
	public ModuleBinding[] getAllRequiredModules() {
		if (this.requiredModules != null)
			return this.requiredModules;

		Collection<ModuleBinding> allRequires = dependencyCollector().get();
		ModuleBinding javaBase = this.environment.getModule(JRTUtil.JAVA_BASE_CHAR);
		if (!CharOperation.equals(this.moduleName, TypeConstants.JAVA_BASE) && javaBase != null) {
			allRequires.add(javaBase);
		}
		return this.requiredModules = allRequires.size() > 0 ? allRequires.toArray(new ModuleBinding[allRequires.size()]) : NO_REQUIRES;
	}

	interface ICharTransType {
		public TypeBinding[] apply(char[][] t);
	}

	Function<char[], TypeBinding> mapper = v -> this.environment.getType(CharOperation.splitOn('.', v));
	ICharTransType cTrans = t -> Stream.of(t).map(e -> this.mapper.apply(e)).toArray(TypeBinding[] :: new);

	public TypeBinding[] getUses() {
		if (this.iUses == null) // either null or already processed
			return this.uses;
		this.uses = this.cTrans.apply(this.iUses);
		this.iUses = null; //processed
		return this.uses;
	}
	public Service[] getServices() {
		if (this.iServices != null) {
			int len = this.iServices.length;
			this.services = new Service[len];
			for (int i = 0; i < len; ++i) {
				IService s = this.iServices[i];
				Service ts = this.services[i] = new Service();
				ts.service = this.environment.getType(CharOperation.splitOn('.', s.name()));
				ts.implementations = this.cTrans.apply(s.with());
			}
			this.iServices = null; // done
		}
		return this.services;
	}
	public char[] name() {
		return this.moduleName;
	}

	private boolean isPackageVisibleTo(PackageBinding pkg, ModuleBinding client, IPackageExport[] pvs) {
		PackageBinding resolved = getExportedPackage(pkg.readableName());
		if (resolved == pkg) {
			if (this.isAuto) { // all packages are exported by an automatic module
				return true;
			}
			Predicate<IPackageExport> isTargeted = IPackageExport::isQualified;
			Predicate<IPackageExport> isExportedTo = e -> 
				Stream.of(e.targets()).map(ref -> this.environment.getModule(ref)).filter(m -> m != null).anyMatch(client::equals);
			
			return Stream.of(pvs).filter(e -> CharOperation.equals(pkg.readableName(), e.name()))
					.anyMatch(isTargeted.negate().or(isExportedTo));
		}
		return false;
	}
	/**
	 * Check if the specified package is exported to the client module by this module. True if the package appears
	 * in the list of exported packages and when the export is targeted, the module appears in the targets of the
	 * exports statement
	 * @param pkg - the package whose visibility is to be checked
	 * @param client - the module that wishes to use the package
	 * @return true if the package is visible to the client module, false otherwise
	 */
	public boolean isPackageExportedTo(PackageBinding pkg, ModuleBinding client) {
		return isPackageVisibleTo(pkg, client, this.exports);
	}
	/**
	 * Check if the specified package is opened to the client module by this module. True if the package appears
	 * in the list of opened packages and when the opens is targeted, the module appears in the targets of the
	 * opens statement
	 * @param pkg - the package whose visibility is to be checked
	 * @param client - the module that wishes to use the package
	 * @return true if the package is visible to the client module, false otherwise
	 */
	public boolean isPackageOpenedTo(PackageBinding pkg, ModuleBinding client) {
		return isPackageVisibleTo(pkg, client, this.opens);
	}
	public PackageBinding getTopLevelPackage(char[] name) {
		// return package binding if there exists a package named name in this module's context and it can be seen by this module
		// A package can be seen by this module if it declares the package or someone exports that package to it
		PackageBinding existing = this.environment.getPackage0(name);
		if (existing != null) {
			if (existing == LookupEnvironment.TheNotFoundPackage)
				return null;
		}
		existing = getDeclaredPackage(new char[][] {name});
		if (existing != null) {
			return existing;
		} else {
			return Stream.of(getAllRequiredModules()).sorted((m1, m2) -> m1.requires.length - m2.requires.length)
					.map(m -> {
						PackageBinding binding = m.getExportedPackage(name);
						if (binding != null && m.isPackageExportedTo(binding, this)) {
							return m.declaredPackages.get(name);
						}
						return null;
					})
			.filter(p -> p != null).findFirst().orElse(null);
		}
	}
	// Given parent is declared in this module, see if there is sub package named name declared in this module
	private PackageBinding getDeclaredPackage(PackageBinding parent, char[] name) {
		PackageBinding pkg = parent.getPackage0(name);
		if (pkg != null && pkg != LookupEnvironment.TheNotFoundPackage)
			return pkg;
		if (declaresPackage(parent.compoundName, name)) {
			char[][] subPkgCompoundName = CharOperation.arrayConcat(parent.compoundName, name);
			PackageBinding binding = new PackageBinding(subPkgCompoundName, parent, this.environment);
			parent.addPackage(binding);
			this.declaredPackages.put(binding.readableName(), binding);
			return binding;
		}
		// TODO: Situation can probably improved by adding NOtFoundPackage to this.declaredPackages 
		//parent.addNotFoundPackage(name); Not a package in this module does not mean not a package at all
		return null;
	}
	public PackageBinding getDeclaredPackage(char[][] name) {
		// return package binding if there exists a package named name in this module
		if (name == null || name.length == 0) {
			return this.environment.getDefaultPackage(this.moduleName);
		}

		PackageBinding parent = null;
		PackageBinding existing = this.environment.getPackage0(name[0]); 
		if (existing != null) { // known top level package
			if (existing == LookupEnvironment.TheNotFoundPackage)
				return null;
			parent = existing;
		}
		if (parent == null) {
			if (declaresPackage(null, name[0])) { // unknown as yet, but declared in this module
				parent = new PackageBinding(name[0], this.environment);
				this.declaredPackages.put(name[0], parent);
			} else {
				this.declaredPackages.put(name[0], LookupEnvironment.TheNotFoundPackage); // not declared in this module
				return null;
			}
		} else if (!declaresPackage(null, name[0])) { // already seen before, but not declared in this module
			return null;
		}
		// check each sub package
		for (int i = 1; i < name.length; i++) {
			PackageBinding binding = getDeclaredPackage(parent, name[i]); 
			if (binding == null) {
				return null;
			}
			parent = binding;
		}
		return parent;
	}
	private PackageBinding getVisibilityPackage(char[] qualifiedPackageName, IPackageExport[]  pvs) {
		PackageBinding existing = this.exportedPackages.get(qualifiedPackageName);
		if (existing != null && existing != LookupEnvironment.TheNotFoundPackage)
			return existing;
		if (this.isAuto) { // all packages are exported by an automatic module
			return getDeclaredPackage(CharOperation.splitOn('.', qualifiedPackageName));
		}
		//Resolve exports to see if the package or a sub package is exported
		return Stream.of(pvs).sorted((e1, e2) -> e1.name().length - e2.name().length)
		.filter(e -> CharOperation.prefixEquals(qualifiedPackageName, e.name())) // TODO: improve this
		.map(e -> {
			PackageBinding binding = getDeclaredPackage(CharOperation.splitOn('.', e.name()));
			if (binding != null) {
				this.exportedPackages.put(e.name(), binding);
				return binding;
			}
			return null;
		}).filter(p -> p != null).findFirst().orElse(null);
	}
	public PackageBinding getExportedPackage(char[] qualifiedPackageName) {
		return getVisibilityPackage(qualifiedPackageName, this.exports);
	}
	public PackageBinding getOpenedPackage(char[] qualifiedPackageName) {
		return getVisibilityPackage(qualifiedPackageName, this.opens);
	}
	public boolean declaresPackage(PackageBinding p) {
		PackageBinding pkg = this.declaredPackages.get(p.readableName());
		if (pkg == null) {
			pkg = getDeclaredPackage(p.compoundName);
			if (pkg == p) {
				this.declaredPackages.put(p.readableName(), p);
				return true;
			}
		}
		return pkg == p;
	}
	public boolean declaresPackage(char[][] parentPackageName, char[] name) {
		char[] qualifiedName = CharOperation.concatWith(parentPackageName, name, '.');
		PackageBinding declared = this.declaredPackages.get(qualifiedName);
		if (declared != null && declared != LookupEnvironment.TheNotFoundPackage) {
 				return true;
		}
		INameEnvironment nameEnvironment = this.environment.nameEnvironment;
		if (nameEnvironment instanceof IModuleAwareNameEnvironment) {
			return ((IModuleAwareNameEnvironment)nameEnvironment).isPackage(parentPackageName, name, getModuleLookupContext());
		} else {
			return nameEnvironment.isPackage(parentPackageName, name);
		}
	}
	public PackageBinding getPackage(char[][] parentPackageName, char[] packageName) {
		// Returns a package binding if there exists such a package in the context of this module and it is observable
		// A package is observable if it is declared in this module or it is exported by some required module
		PackageBinding binding = null;
		if (parentPackageName == null || parentPackageName.length == 0) {
			binding = getTopLevelPackage(packageName);
		} else {
			binding = getDeclaredPackage(parentPackageName);
			if (binding != null && binding != LookupEnvironment.TheNotFoundPackage) {
				binding = getDeclaredPackage(binding, packageName);
				if (binding != null)
					return binding;
			}
		}
		if (binding == null) {
			char[] qualifiedPackageName = CharOperation.concatWith(parentPackageName, packageName, '.');
			return Stream.of(getAllRequiredModules())
					.map(m -> {
						if (m.isAuto) {
							return m.getPackage(parentPackageName, packageName);
						}
						PackageBinding p = m.getExportedPackage(qualifiedPackageName);
						if (p != null && m.isPackageExportedTo(p, this)) {
							return m.declaredPackages.get(qualifiedPackageName);
						}
						return null;
					})
			.filter(p -> p != null).findFirst().orElse(null);
		}
		return binding;
	}
	/**
	 * Check if the given package is visible to this module. True when the package is declared in
	 * this module or exported by some required module to this module.
	 * See {@link #isPackageExportedTo(PackageBinding, ModuleBinding)}
	 * 
	 * @param pkg
	 * 
	 * @return True, if the package is visible to this module, false otherwise
	 */
	public boolean canSee(PackageBinding pkg) {
		return declaresPackage(pkg) || Stream.of(getAllRequiredModules()).anyMatch(
				dep -> dep.isPackageExportedTo(pkg, ModuleBinding.this)
		);
	}
	@Override
	public char[] computeUniqueKey(boolean isLeaf) {
		return this.moduleName; // for now just return the module name - tied to Signature if any.
	}
	public boolean dependsOn(ModuleBinding other) {
 		if (other == this)
 			return true;
		return Stream.of(getAllRequiredModules()).anyMatch(other::equals);
	}
	// A context representing just this module
 	public IModuleContext getModuleLookupContext() {
 		IModuleAwareNameEnvironment env = (IModuleAwareNameEnvironment) this.environment.nameEnvironment;
 		IModuleEnvironment moduleEnvironment = env.getModuleEnvironmentFor(this.moduleName);
 		return () -> moduleEnvironment == null ? Stream.empty() : Stream.of(moduleEnvironment);
 	}
 	// A context including this module and all it's required modules
 	public IModuleContext getDependencyClosureContext() {
 		if (this.isAuto)
 			return IModuleContext.UNNAMED_MODULE_CONTEXT;
 		ModuleBinding[] deps = getAllRequiredModules();
 		return getModuleLookupContext().includeAll(Stream.of(deps).map(m -> m.getModuleLookupContext()));
 	}
 	// A context that includes the entire module graph starting from this module
 	public IModuleContext getModuleGraphContext() {
 		Stream<ModuleBinding> reqs = getRequiredModules(false);
 		return getModuleLookupContext().includeAll(reqs.map(m -> m.getModuleGraphContext()).distinct());
 	}
	@Override
	public int kind() {
		//
		return Binding.MODULE;
	}

	@Override
	public char[] readableName() {
		//
		return this.moduleName;
	}

	private void printPackageVisibility(StringBuffer buffer, IPackageExport[] pvs, String present, String absent) {
		int len = pvs.length;
		buffer.append(len > 0 ? present : absent);
		for (int i = 0; i < len; i++) {
			IPackageExport pv = pvs[i];
			buffer.append("\n\t"); //$NON-NLS-1$
			buffer.append(pv.name());
			char[][] targets = pv.targets();
			if (targets != null) {
				buffer.append("to "); //$NON-NLS-1$
				for (int j = 0; j < targets.length; j++) {
					if (j != 0)
						buffer.append(", "); //$NON-NLS-1$
					buffer.append(targets[j]);
				}
			}
		}
	}
	public String toString() {
		StringBuffer buffer = new StringBuffer(30);
		buffer.append("module " + new String(readableName())); //$NON-NLS-1$
		if (this.requires.length > 0) {
			buffer.append("\n/*    requires    */\n"); //$NON-NLS-1$
			for (int i = 0; i < this.requires.length; i++) {
				buffer.append("\n\t"); //$NON-NLS-1$
				if (this.requires[i].isTransitive())
					buffer.append("public "); //$NON-NLS-1$
				buffer.append(this.requires[i].name());
			}
		} else {
			buffer.append("\nNo Requires"); //$NON-NLS-1$
		}
		printPackageVisibility(buffer, this.exports, "\n/*    exports    */\n", "\nNo Exports"); //$NON-NLS-1$ //$NON-NLS-2$
		printPackageVisibility(buffer, this.opens, "\n/*    opens    */\n", "\nNo Opens"); //$NON-NLS-1$ //$NON-NLS-2$
		if (this.uses != null && this.uses.length > 0) {
			buffer.append("\n/*    uses    /*\n"); //$NON-NLS-1$
			for (int i = 0; i < this.uses.length; i++) {
				buffer.append("\n\t"); //$NON-NLS-1$
				buffer.append(this.uses[i].debugName());
			}
		} else {
			buffer.append("\nNo Uses"); //$NON-NLS-1$
		}
		if (this.services != null && this.services.length > 0) {
			buffer.append("\n/*    Services    */\n"); //$NON-NLS-1$
			for (Service s : this.services) {
				buffer.append("\nprovides "); //$NON-NLS-1$
				buffer.append(s.service.qualifiedSourceName());
				buffer.append(" with "); //$NON-NLS-1$
				buffer.append(Stream.of(s.implementations).map(t -> new String(t.qualifiedSourceName())).collect(Collectors.joining(", ")));//$NON-NLS-1$
				buffer.append(";"); //$NON-NLS-1$
			}
		} else {
			buffer.append("\nNo Services"); //$NON-NLS-1$
		}
		return buffer.toString();
	}
}
