package hudson.plugins.disk_usage;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.ItemGroup;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;

/**
 * Build listener for calculation build disk usage
 * 
 * @author Lucie Votypkova
 */
@Extension
public class DiskUsageBuildListener extends RunListener<AbstractBuild>{
    
    @Override
    public void onCompleted(AbstractBuild build, TaskListener listener){
        if(DiskUsageProjectActionFactory.DESCRIPTOR.isExcluded(build.getProject())){
            listener.getLogger().println("This job is excluded form disk usage calculation.");
            return;
        }
        try{
            //count build.xml too
            build.save();
            DiskUsagePlugin plugin = Jenkins.getInstance().getPlugin(DiskUsagePlugin.class);
            listener.getLogger().println("Started calculate disk usage of build");
            Long startTimeOfBuildCalculation = System.currentTimeMillis();
                DiskUsageUtil.calculateDiskUsageForBuild(build);
                listener.getLogger().println("Finished Calculation of disk usage of build in " + DiskUsageUtil.formatTimeInMilisec(System.currentTimeMillis() - startTimeOfBuildCalculation));
                DiskUsageProperty property = (DiskUsageProperty) build.getProject().getProperty(DiskUsageProperty.class);
                if(property==null){
                    property = new DiskUsageProperty();
                    build.getProject().addProperty(property);
                }
                if(build.getWorkspace()!=null){
                    ArrayList<FilePath> exceededFiles = new ArrayList<FilePath>();
                    AbstractProject project = build.getProject();
                    Node node = build.getBuiltOn();
                    if(project instanceof ItemGroup){
                        List<AbstractProject> projects = DiskUsageUtil.getAllProjects((ItemGroup) project);
                        for(AbstractProject p: projects){
                            DiskUsageProperty prop = (DiskUsageProperty) p.getProperty(DiskUsageProperty.class);
                            if(prop==null){
                                prop = new DiskUsageProperty();
                                p.addProperty(prop);
                            }
                            prop.checkWorkspaces();
                            Map<String,Long> paths = prop.getSlaveWorkspaceUsage().get(node.getNodeName());
                            if(paths!=null && !paths.isEmpty()){
                                for(String path: paths.keySet()){
                                    exceededFiles.add(new FilePath(node.getChannel(),path));
                                }
                            }
                        }
                    }
                    property.checkWorkspaces();
                    listener.getLogger().println("Started calculate disk usage of workspace");
                    Long startTimeOfWorkspaceCalculation = System.currentTimeMillis();
                    Long size = DiskUsageUtil.calculateWorkspaceDiskUsageForPath(build.getWorkspace(),exceededFiles);
                    listener.getLogger().println("Finished Calculation of disk usage of workspace in " + DiskUsageUtil.formatTimeInMilisec(System.currentTimeMillis() - startTimeOfWorkspaceCalculation));
                    property.putSlaveWorkspaceSize(build.getBuiltOn(), build.getWorkspace().getRemote(), size);
                    property.saveDiskUsage();
                    DiskUsageUtil.controlorkspaceExceedSize(project);
                }
            }
            catch(Exception ex){
                listener.getLogger().println("Disk usage plugin fails during calculation disk usage of this build.");
                    Logger.getLogger(getClass().getName()).log(Level.WARNING, "Disk usage plugin fails during build calculation disk space of job " + build.getParent().getDisplayName(), ex);
            }
        }
}
