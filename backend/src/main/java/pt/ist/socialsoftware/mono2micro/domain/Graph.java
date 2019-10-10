package pt.ist.socialsoftware.mono2micro.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.management.openmbean.KeyAlreadyExistsException;

import pt.ist.socialsoftware.mono2micro.utils.Metrics;

public class Graph {
	private String codebaseName;
	private String dendrogramName;
	private String name;
	private boolean expert;
	private float cutValue;
	private String cutType;
	private float silhouetteScore;
	private float complexity;
	private List<Controller> controllers = new ArrayList<>();
	private List<Cluster> clusters = new ArrayList<>();

	public Graph() {
	}

	public String getCodebaseName() {
		return this.codebaseName;
	}

	public void setCodebaseName(String codebaseName) {
		this.codebaseName = codebaseName;
	}

	public String getDendrogramName() {
		return this.dendrogramName;
	}

	public void setDendrogramName(String dendrogramName) {
		this.dendrogramName = dendrogramName;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public boolean isExpert() {
		return expert;
	}

	public void setExpert(boolean expert) {
		this.expert = expert;
	}

	public float getCutValue() {
		return this.cutValue;
	}

	public void setCutValue(float cutValue) {
		this.cutValue = cutValue;
	}

	public String getCutType() {
		return cutType;
	}

	public void setCutType(String cutType) {
		this.cutType = cutType;
	}

	public float getSilhouetteScore() {
		return this.silhouetteScore;
	}

	public void setSilhouetteScore(float silhouetteScore) {
		this.silhouetteScore = silhouetteScore;
	}

	public float getComplexity() {
		return complexity;
	}

	public void setComplexity(float complexity) {
		this.complexity = complexity;
	}

	public List<Controller> getControllers() {
		return this.controllers;
	}

	public void addController(Controller controller) {
		this.controllers.add(controller);
	}

	public List<Cluster> getClusters() {
		return this.clusters;
	}

	public void addCluster(Cluster cluster) {
		this.clusters.add(cluster);
	}

	public void deleteCluster(String clusterName) {
		for (int i = 0; i < this.clusters.size(); i++) {
			if (this.clusters.get(i).getName().equals(clusterName)) {
				this.clusters.remove(i);
				break;
			}
		}
	}

	public void mergeClusters(String cluster1, String cluster2, String newName) {
		Cluster mergedCluster = new Cluster(newName);
		for (int i = 0; i < clusters.size(); i++) {
			if (clusters.get(i).getName().equals(cluster1)) {
				for (Entity entity : clusters.get(i).getEntities())
					mergedCluster.addEntity(entity);
				clusters.remove(i);
				break;
			}
		}
		for (int i = 0; i < clusters.size(); i++) {
			if (clusters.get(i).getName().equals(cluster2)) {
				for (Entity entity : clusters.get(i).getEntities())
					mergedCluster.addEntity(entity);
				clusters.remove(i);
				break;
			}
		}
		this.addCluster(mergedCluster);
		this.calculateMetrics();
	}

	public void renameCluster(String clusterName, String newName) {
		if (this.getClustersNames().contains(newName)) {
			throw new KeyAlreadyExistsException();
		}
		for (int i = 0; i < clusters.size(); i++) {
			if (clusters.get(i).getName().equals(clusterName)) {
				clusters.get(i).setName(newName);
				break;
			}
		}
		this.calculateMetrics();
	}

	public List<String> getClustersNames() {
		List<String> clustersNames = new ArrayList<>();
		for (Cluster cluster : this.clusters)
			clustersNames.add(cluster.getName());
		return clustersNames;
	}

	public Cluster getCluster(String clusterName) {
		for (Cluster cluster : this.clusters)
			if (cluster.getName().equals(clusterName))
				return cluster;
		return null;
	}

	public Cluster getClusterWithEntity(String entityName) {
		for (Cluster cluster : this.clusters) {
			if (cluster.containsEntity(entityName))
				return cluster;
		}
		return null;
	}

	public void splitCluster(String clusterName, String newName, String[] entities) {
		Cluster currentCluster = this.getCluster(clusterName);
		Cluster newCluster = new Cluster(newName);
		for (String entity : entities) {
			newCluster.addEntity(currentCluster.getEntity(entity));
			currentCluster.removeEntity(entity);
		}
		this.addCluster(newCluster);
		this.calculateMetrics();
	}

	public void transferEntities(String fromCluster, String toCluster, String[] entities) {
		Cluster c1 = this.getCluster(fromCluster);
		Cluster c2 = this.getCluster(toCluster);
		for (String entity : entities) {
			c2.addEntity(c1.getEntity(entity));
			c1.removeEntity(entity);
		}
		this.calculateMetrics();
	}

	public void moveEntities(String[] entities, String targetCluster) {
		List<String> removedEntities = new ArrayList<>();
        for (Cluster cluster : this.clusters) {
			for (String entity : entities) {
				if (cluster.containsEntity(entity) && !cluster.getName().equals(targetCluster)) {
					cluster.removeEntity(entity);
					removedEntities.add(entity);
				}
			}
		}
		for (String entity : removedEntities)
			this.getCluster(targetCluster).addEntity(new Entity(entity));
	}

	public Map<String,List<Controller>> getClusterControllers() {
		Map<String,List<Controller>> clusterControllers = new HashMap<>();

		for (Cluster cluster : this.clusters) {
			List<Controller> touchedControllers = new ArrayList<>();
			for (Controller controller : this.controllers) {
				for (String controllerEntity : controller.getEntities().keySet()) {
					if (cluster.containsEntity(controllerEntity)) {
						touchedControllers.add(controller);
						break;
					}
				}
			}
			clusterControllers.put(cluster.getName(), touchedControllers);
		}
		return clusterControllers;
	}

	public Map<String,List<Cluster>> getControllerClusters() {
		Map<String,List<Cluster>> controllerClusters = new HashMap<>();

		for (Controller controller : this.controllers) {
			List<Cluster> touchedClusters = new ArrayList<>();
			for (Cluster cluster : this.clusters) {
				for (Entity clusterEntity : cluster.getEntities()) {
					if (controller.containsEntity(clusterEntity.getName())) {
						touchedClusters.add(cluster);
						break;
					}
				}
			}
			controllerClusters.put(controller.getName(), touchedClusters);
		}
		return controllerClusters;
	}

	public void calculateMetrics() {
		Metrics metrics = new Metrics(this);
		metrics.calculateMetrics();
	}
}