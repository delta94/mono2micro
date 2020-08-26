package pt.ist.socialsoftware.mono2micro.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import pt.ist.socialsoftware.mono2micro.domain.*;

public class Metrics {
    private Graph graph;
	private Map<String,List<Cluster>> controllerClusters;
	private Map<String,List<Controller>> clusterControllers;

    public Metrics(Graph graph) {
        this.graph = graph;
		this.controllerClusters = graph.getControllerClusters();
		this.clusterControllers = graph.getClusterControllers();
    }

    public void calculateMetrics() {
		float graphComplexity = 0;
		float graphCohesion = 0;
		float graphCoupling = 0;

		for (Cluster cluster : graph.getClusters()) {
			cluster.setCouplingDependencies(new HashMap<>());
		}
		
		for (Controller controller : graph.getControllers()) {
			calculateControllerComplexity(controller);
			calculateRedesignComplexities(controller, Constants.DEFAULT_REDESIGN_NAME);
			graphComplexity += controller.getComplexity();
			calculateClusterDependencies(controller);
		}

		graphComplexity /= graph.getControllers().size();
		graphComplexity = BigDecimal.valueOf(graphComplexity).setScale(2, RoundingMode.HALF_UP).floatValue();
		this.graph.setComplexity(graphComplexity);

		for (Cluster cluster : graph.getClusters()) {
			calculateClusterComplexity(cluster);

			calculateClusterCohesion(cluster);
			graphCohesion += cluster.getCohesion();

			calculateClusterCoupling(cluster);
			graphCoupling += cluster.getCoupling();
		}

		graphCohesion /= graph.getClusters().size();
		graphCohesion = BigDecimal.valueOf(graphCohesion).setScale(2, RoundingMode.HALF_UP).floatValue();
		this.graph.setCohesion(graphCohesion);
		graphCoupling /= graph.getClusters().size();
		graphCoupling = BigDecimal.valueOf(graphCoupling).setScale(2, RoundingMode.HALF_UP).floatValue();
		this.graph.setCoupling(graphCoupling);
    }

	private void calculateClusterDependencies(Controller controller) {
    	try {
			JSONArray accessSequence = new JSONArray(controller.getEntitiesSeq());
			for (int i = 0; i < accessSequence.length() - 1; i++) {
				JSONObject clusterAccess = accessSequence.getJSONObject(i);
				String fromCluster = clusterAccess.getString("cluster");
				Cluster c1 = graph.getCluster(fromCluster);

				JSONObject nextClusterAccess = accessSequence.getJSONObject(i + 1);
				String toCluster = nextClusterAccess.getString("cluster");
				String toEntity = nextClusterAccess.getJSONArray("sequence").getJSONArray(0).getString(0);

				c1.addCouplingDependency(toCluster, toEntity);
			}
		} catch (JSONException e) {
    		e.printStackTrace();
		}
	}

	private void calculateControllerComplexity(Controller controller) {
    	if (this.controllerClusters.get(controller.getName()).size() == 1) {
    		controller.setComplexity(0);
    		return;
		}

		float complexity = 0;
		Set<String> clusterAccessDependencies = new HashSet<>();
		try {
			JSONArray accessSequence = new JSONArray(controller.getEntitiesSeq());

			for (int i = 0; i < accessSequence.length(); i++) {
				JSONObject clusterAccess = accessSequence.getJSONObject(i);
				JSONArray entitiesSequence = clusterAccess.getJSONArray("sequence");
				for (int j = 0; j < entitiesSequence.length(); j++) {
					JSONArray entityAccess = entitiesSequence.getJSONArray(j);
					String entity = entityAccess.getString(0);
					String mode = entityAccess.getString(1);

					if (mode.equals("R"))
						costOfRead(controller, entity, clusterAccessDependencies);
					else
						costOfWrite(controller, entity, clusterAccessDependencies);
				}

				complexity += clusterAccessDependencies.size();
				clusterAccessDependencies.clear();
			}

			controller.setComplexity(complexity);
		} catch (JSONException e) {
			e.printStackTrace();
		}
    }

    private void costOfRead(Controller controller, String entity, Set<String> clusterAccessDependencies){
        for (Controller otherController : this.graph.getControllers()) {
            if (!otherController.getName().equals(controller.getName()) &&
                otherController.containsEntity(entity) && 
                otherController.getEntities().get(entity).contains("W") &&
            	this.controllerClusters.get(otherController.getName()).size() > 1) {
					clusterAccessDependencies.add(otherController.getName());
                }
        }
    }

    private void costOfWrite(Controller controller, String entity, Set<String> clusterAccessDependencies){
        for (Controller otherController : this.graph.getControllers()) {
            if (!otherController.getName().equals(controller.getName()) &&
                otherController.containsEntity(entity) && 
                otherController.getEntities().get(entity).contains("R") &&
				this.controllerClusters.get(otherController.getName()).size() > 1) {
					clusterAccessDependencies.add(otherController.getName());
                }
        }
    }

	private void calculateClusterComplexity(Cluster cluster) {
    	List<Controller> controllersThatAccessThisCluster = this.clusterControllers.get(cluster.getName());

		float complexity = 0;
		for (Controller controller : controllersThatAccessThisCluster) {
			complexity += controller.getComplexity();
		}
		complexity /= controllersThatAccessThisCluster.size();
		complexity = BigDecimal.valueOf(complexity).setScale(2, RoundingMode.HALF_UP).floatValue();
		cluster.setComplexity(complexity);
	}

	public void calculateClusterCohesion(Cluster cluster) {
		List<Controller> controllersThatAccessThisCluster = this.clusterControllers.get(cluster.getName());

		float cohesion = 0;
		for (Controller controller : controllersThatAccessThisCluster) {
			float numberEntitiesTouched = 0;
			for (String controllerEntity : controller.getEntities().keySet()) {
				if (cluster.containsEntity(controllerEntity))
					numberEntitiesTouched++;
			}
			cohesion += numberEntitiesTouched / cluster.getEntities().size();
		}
		cohesion /= controllersThatAccessThisCluster.size();
		cohesion = BigDecimal.valueOf(cohesion).setScale(2, RoundingMode.HALF_UP).floatValue();
		cluster.setCohesion(cohesion);
	}

	private void calculateClusterCoupling(Cluster c1) {
    	float coupling = 0;
    	for (String c2 : c1.getCouplingDependencies().keySet()) {
    		coupling += c1.getCouplingDependencies().get(c2).size();
		}
		coupling = graph.getClusters().size() == 1 ? 0 : coupling / (graph.getClusters().size() - 1);
		coupling = BigDecimal.valueOf(coupling).setScale(2, RoundingMode.HALF_UP).floatValue();
		c1.setCoupling(coupling);
	}

	public void calculateRedesignComplexities(Controller controller, String redesignName){
		FunctionalityRedesign functionalityRedesign = controller.getFunctionalityRedesign(redesignName);
		functionalityRedesign.setFunctionalityComplexity(0);
		functionalityRedesign.setSystemComplexity(0);

		for (int i = 0; i < functionalityRedesign.getRedesign().size(); i++) {
			LocalTransaction lt = functionalityRedesign.getRedesign().get(i);

			if(!lt.getId().equals(String.valueOf(-1))){
				try {
					JSONArray sequence = new JSONArray(lt.getAccessedEntities());
					for(int j=0; j < sequence.length(); j++){
						String entity = sequence.getJSONArray(j).getString(0);
						String accessMode = sequence.getJSONArray(j).getString(1);

						if(accessMode.contains("W")){
							if(lt.getType() == LocalTransactionTypes.COMPENSATABLE) {
								functionalityRedesign.setFunctionalityComplexity(functionalityRedesign.getFunctionalityComplexity() + 1);
								calculateSystemComplexity(entity, functionalityRedesign);
							}
						}

						if (accessMode.contains("R")) {
							functionalityComplexityCostOfRead(entity, controller, functionalityRedesign);
						}
					}
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private void calculateSystemComplexity(String entity, FunctionalityRedesign functionalityRedesign) {
		for (Controller otherController : this.graph.getControllers()) {
			if (!otherController.getName().equals(functionalityRedesign.getName()) &&
					otherController.containsEntity(entity) &&
					otherController.getEntities().get(entity).contains("R") &&
					this.controllerClusters.get(otherController.getName()).size() > 1) {
				functionalityRedesign.setSystemComplexity(functionalityRedesign.getSystemComplexity() + 1);
			}
		}
	}

	private void functionalityComplexityCostOfRead(String entity, Controller controller, FunctionalityRedesign functionalityRedesign) throws JSONException {
		for (Controller otherController : this.graph.getControllers()) {
			if (!otherController.getName().equals(controller.getName()) &&
					otherController.containsEntity(entity) &&
					this.controllerClusters.get(otherController.getName()).size() > 1) {

				if(functionalityRedesign.getPivotTransaction().equals("") &&
						otherController.getEntities().get(entity).contains("W")){
					functionalityRedesign.setFunctionalityComplexity(functionalityRedesign.getFunctionalityComplexity() + 1);
				}
				else if(!functionalityRedesign.getPivotTransaction().equals("") &&
						functionalityRedesign.semanticLockEntities().contains(entity)){
					functionalityRedesign.setFunctionalityComplexity(functionalityRedesign.getFunctionalityComplexity() + 1);
				}

			}
		}
	}
}