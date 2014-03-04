package com.levigo.m2e.gwt;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.core.project.configurator.AbstractProjectConfigurator;
import org.eclipse.m2e.core.project.configurator.ProjectConfigurationRequest;
import org.eclipse.m2e.jdt.IClasspathDescriptor;
import org.eclipse.m2e.jdt.IClasspathEntryDescriptor;
import org.eclipse.m2e.jdt.IJavaProjectConfigurator;

/**
 * @author Stefan Wokusch
 */
public class GwtPluginConfigurator extends AbstractProjectConfigurator implements IJavaProjectConfigurator {

  @Override
  public void configure(ProjectConfigurationRequest request, IProgressMonitor monitor) throws CoreException {

  }

  @Override
  public void configureRawClasspath(ProjectConfigurationRequest request, IClasspathDescriptor classpath,
      IProgressMonitor monitor) throws CoreException {

    final IProject project = request.getProject();
    final IJavaProject javaProject = JavaCore.create(project);
    final IMavenProjectFacade facade = request.getMavenProjectFacade();

    // Managing SuperSources
    {
      for (IPath source : facade.getCompileSourceLocations()) {
        source = javaProject.getPath().append(source);

        for (String superSourcePath : findSuperSource(source)) {
          final IPath path = new Path(null, superSourcePath);

          // final IPath toAdd = source.append(path);

          for (IClasspathEntryDescriptor e : classpath.getEntryDescriptors())
            if (e.getPath().equals(source)) {
              e.addExclusionPattern(path);
            }

          // only exclude - do NOT add to Source-Path (e.g. emulation folder)
          // classpath.addSourceEntry(toAdd, facade.getOutputLocation(), false);
        }
      }
    }
    // Bugfix for not getting the Resources not from Resource-folder
    // http://code.google.com/p/google-web-toolkit/issues/detail?id=4600
    {
      for (IPath resource : facade.getResourceLocations()) {
        resource = javaProject.getPath().append(resource);
        for (IClasspathEntryDescriptor e : classpath.getEntryDescriptors())
          if (e.getPath().equals(resource)) {
            e.setExclusionPatterns(new IPath[]{});
          }
      }
    }
  }

  private Collection<String> findSuperSource(IPath source) {
    HashSet<String> superSources = new HashSet<String>();

    final IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
    final IFolder folder = root.getFolder(source);
    File file = new File(folder.getLocationURI());

    for (File gwtXmlPath : findGwtXmls(file)) {
      final String relativePath = checkForSuperSoruces(gwtXmlPath);
      if (relativePath != null) {
        String folderPath = gwtXmlPath.getParent().substring(file.getAbsolutePath().length());
        folderPath = folderPath.substring(1);// Without first "/"
        superSources.add(folderPath + relativePath);
      }
    }

    return superSources;
  }

  private String checkForSuperSoruces(File gwtXmlPath) {
    String source = readFileAsString(gwtXmlPath);

    int index = source.indexOf("<super-source");
    if (index >= 0) {
      System.out.println("Found supersource in " + gwtXmlPath);
      // TODO Support path attribute in supersource
      return "/";// Needs to end with "/"
    }
    System.out.println("No Supersource found in " + gwtXmlPath);
    return null;
  }

  private Collection<File> findGwtXmls(File source) {
    HashSet<File> superSources = new HashSet<File>();
    findGwtXmls(source, superSources);
    return superSources;
  }

  private void findGwtXmls(File source, Collection<File> gwtXmls) {
    if (source.getName().endsWith(".gwt.xml"))
      gwtXmls.add(source);

    if (source.isDirectory())
      for (File f : source.listFiles())
        findGwtXmls(f, gwtXmls);
  }


  @Override
  public void configureClasspath(IMavenProjectFacade facade, IClasspathDescriptor classpath, IProgressMonitor monitor)
      throws CoreException {
    // All Done in getRawClassPath
  }

  private static String readFileAsString(File filePath) {
    try {
      StringBuffer fileData = new StringBuffer(1000);
      BufferedReader reader = new BufferedReader(new FileReader(filePath));
      char[] buf = new char[1024];
      int numRead = 0;
      while ((numRead = reader.read(buf)) != -1) {
        fileData.append(buf, 0, numRead);
      }
      reader.close();
      return fileData.toString();
    } catch (IOException e) {
      System.out.println("Ignoring file, because of Exception while reading " + filePath);
      e.printStackTrace();
      return "";
    }
  }
}