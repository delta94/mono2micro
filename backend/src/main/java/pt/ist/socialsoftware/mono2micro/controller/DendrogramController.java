package pt.ist.socialsoftware.mono2micro.controller;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pt.ist.socialsoftware.mono2micro.domain.Cluster;
import pt.ist.socialsoftware.mono2micro.domain.Codebase;
import pt.ist.socialsoftware.mono2micro.domain.Controller;
import pt.ist.socialsoftware.mono2micro.domain.Dendrogram;
import pt.ist.socialsoftware.mono2micro.domain.Entity;
import pt.ist.socialsoftware.mono2micro.domain.Expert;
import pt.ist.socialsoftware.mono2micro.domain.Graph;
import pt.ist.socialsoftware.mono2micro.dto.AnalysisDto;
import pt.ist.socialsoftware.mono2micro.manager.CodebaseManager;
import pt.ist.socialsoftware.mono2micro.utils.Pair;
import pt.ist.socialsoftware.mono2micro.utils.PropertiesManager;

@RestController
@RequestMapping(value = "/mono2micro/codebase/{codebaseName}")
public class DendrogramController {

	private static final String PYTHON = PropertiesManager.getProperties().getProperty("python");

	private static Logger logger = LoggerFactory.getLogger(DendrogramController.class);

	private String resourcesPath = "src/main/resources/";

	private String codebaseFolder = "src/main/resources/codebases/";

	private CodebaseManager codebaseManager = new CodebaseManager();



	@RequestMapping(value = "/analyser", method = RequestMethod.GET)
	public ResponseEntity<Float> analyser(@PathVariable String codebaseName,
											@RequestParam String expertName,
											@RequestParam float accessMetricWeight,
											@RequestParam float writeMetricWeight,
											@RequestParam float readMetricWeight,
											@RequestParam float sequenceMetric1Weight,
											@RequestParam float sequenceMetric2Weight,
											@RequestParam float sequenceMetric3Weight,
											@RequestParam float numberClusters) {
		logger.debug("analyser");

		float fmeasure;

		Codebase codebase = codebaseManager.getCodebase(codebaseName);
		
		try {
			Map<String,List<Pair<String,String>>> entityControllers = new HashMap<>();
			Map<String,Integer> e1e2PairCount = new HashMap<>();
			JSONArray similarityMatrix = new JSONArray();
			JSONObject dendrogramData = new JSONObject();


			//read datafile
			InputStream is = new FileInputStream(codebaseFolder + codebase.getName() + ".txt");
			JSONObject datafileJSON = new JSONObject(IOUtils.toString(is, "UTF-8"));
			is.close();

			for (String profile : codebase.getProfiles().keySet()) {
				for (String controllerName : codebase.getProfile(profile)) {

					JSONArray entities = datafileJSON.getJSONArray(controllerName);
					for (int i = 0; i < entities.length(); i++) {
						JSONArray entityArray = entities.getJSONArray(i);
						String entity = entityArray.getString(0);
						String mode = entityArray.getString(1);

						if (entityControllers.containsKey(entity)) {
							boolean containsController = false;
							for (Pair<String,String> controllerPair : entityControllers.get(entity)) {
								if (controllerPair.getFirst().equals(controllerName)) {
									containsController = true;
									if (!controllerPair.getSecond().contains(mode))
										controllerPair.setSecond("RW");
									break;
								}
							}
							if (!containsController) {
								entityControllers.get(entity).add(new Pair<String,String>(controllerName,mode));
							}
						} else {
							List<Pair<String,String>> controllersPairs = new ArrayList<>();
							controllersPairs.add(new Pair<String,String>(controllerName,mode));
							entityControllers.put(entity, controllersPairs);
						}

						if (i < entities.length() - 1) {
							JSONArray nextEntityArray = entities.getJSONArray(i+1);
							String nextEntity = nextEntityArray.getString(0);

							if (!entity.equals(nextEntity)) {
								String e1e2 = entity + "->" + nextEntity;
								String e2e1 = nextEntity + "->" + entity;

								if (e1e2PairCount.containsKey(e1e2)) {
									e1e2PairCount.put(e1e2, e1e2PairCount.get(e1e2) + 1);
								} else {
									int count = e1e2PairCount.containsKey(e2e1) ? e1e2PairCount.get(e2e1) : 0;
									e1e2PairCount.put(e2e1, count + 1);
								}
							}
						}
					}
				}
			}

			List<String> entitiesList = new ArrayList<String>(entityControllers.keySet());
			Collections.sort(entitiesList);

			int maxNumberOfPairs = Collections.max(e1e2PairCount.values());

			JSONArray seq1SimilarityMatrix = new JSONArray();
			JSONArray seq2SimilarityMatrix = new JSONArray();
			JSONArray seq3SimilarityMatrix = new JSONArray();
			for (int i = 0; i < entitiesList.size(); i++) {
				String e1 = entitiesList.get(i);
				JSONArray seq1MatrixAux = new JSONArray();
				JSONArray seq2MatrixAux = new JSONArray();
				JSONArray seq3MatrixAux = new JSONArray();
				for (int j = 0; j < entitiesList.size(); j++) {
					String e2 = entitiesList.get(j);
					if (e1.equals(e2)) {
						seq1MatrixAux.put(new Float(maxNumberOfPairs));
						seq2MatrixAux.put(new Float(1));
						seq3MatrixAux.put(new Float(1));
					} else {
						String e1e2 = e1 + "->" + e2;
						String e2e1 = e2 + "->" + e1;
						float e1e2Count = e1e2PairCount.containsKey(e1e2) ? e1e2PairCount.get(e1e2) : 0;
						float e2e1Count = e1e2PairCount.containsKey(e2e1) ? e1e2PairCount.get(e2e1) : 0;

						seq1MatrixAux.put(new Float(e1e2Count + e2e1Count));
						seq2MatrixAux.put(new Float(e1e2Count + e2e1Count));
						seq3MatrixAux.put(new Float(e1e2Count + e2e1Count));
					}
				}

				List<Float> seq3List = new ArrayList<>();
				for (int k = 0; k < seq3MatrixAux.length(); k++) {
					seq3List.add((float)seq3MatrixAux.get(k));
				}

				float seq3Max = Collections.max(seq3List);

				for (int j = 0; j < entitiesList.size(); j++) {
					if (!entitiesList.get(j).equals(e1)) {
						seq2MatrixAux.put(j, new Float(((float)seq2MatrixAux.get(j)) / maxNumberOfPairs));
						seq3MatrixAux.put(j, new Float(((float)seq3MatrixAux.get(j)) / seq3Max));
					}
				}

				seq1SimilarityMatrix.put(seq1MatrixAux);
				seq2SimilarityMatrix.put(seq2MatrixAux);
				seq3SimilarityMatrix.put(seq3MatrixAux);
			}

			for (int i = 0; i < entitiesList.size(); i++) {
				String e1 = entitiesList.get(i);
				JSONArray matrixAux = new JSONArray();
				for (int j = 0; j < entitiesList.size(); j++) {
					String e2 = entitiesList.get(j);
					float inCommon = 0;
					float inCommonW = 0;
					float inCommonR = 0;
					float e1ControllersW = 0;
					float e1ControllersR = 0;
					for (Pair<String,String> p1 : entityControllers.get(e1)) {
						for (Pair<String,String> p2 : entityControllers.get(e2)) {
							if (p1.getFirst().equals(p2.getFirst()))
								inCommon++;
							if (p1.getFirst().equals(p2.getFirst()) && p1.getSecond().contains("W") && p2.getSecond().contains("W"))
								inCommonW++;
							if (p1.getFirst().equals(p2.getFirst()) && p1.getSecond().equals("R") && p2.getSecond().equals("R"))
								inCommonR++;
						}
						if (p1.getSecond().contains("W"))
							e1ControllersW++;
						if (p1.getSecond().equals("R"))
							e1ControllersR++;
					}

					float accessMetric = inCommon / entityControllers.get(e1).size();
					float writeMetric = e1ControllersW == 0 ? 0 : inCommonW / e1ControllersW;
					float readMetric = e1ControllersR == 0 ? 0 : inCommonR / e1ControllersR;
					float sequence1Metric = (float) seq1SimilarityMatrix.getJSONArray(i).get(j);
					float sequence2Metric = (float) seq2SimilarityMatrix.getJSONArray(i).get(j);
					float sequence3Metric = (float) seq3SimilarityMatrix.getJSONArray(i).get(j);
					float metric = accessMetric * accessMetricWeight / 100 + 
									writeMetric * writeMetricWeight / 100 +
									readMetric * readMetricWeight / 100 +
									sequence1Metric * sequenceMetric1Weight / 100 +
									sequence2Metric * sequenceMetric2Weight / 100 +
									sequence3Metric * sequenceMetric3Weight / 100;
					matrixAux.put(metric);
				}
				similarityMatrix.put(matrixAux);
			}
			dendrogramData.put("matrix", similarityMatrix);
			dendrogramData.put("entities", entitiesList);

			try (FileWriter file = new FileWriter("temp_matrix.json")){
				file.write(dendrogramData.toString());
			} catch (IOException e) {
				e.printStackTrace();
			}

			// run python script with clustering algorithm
			Runtime r = Runtime.getRuntime();
			String pythonScriptPath = resourcesPath + "analyser_dendrogram.py";
			String[] cmd = new String[3];
			cmd[0] = PYTHON;
			cmd[1] = pythonScriptPath;
			cmd[2] = Float.toString(numberClusters);
			
			Process p = r.exec(cmd);

			p.waitFor();

			BufferedReader bre = new BufferedReader(new InputStreamReader(p.getInputStream()));

			is = new FileInputStream("temp_clusters.txt");
			JSONObject json = new JSONObject(IOUtils.toString(is, "UTF-8"));

			Iterator<String> clusters = json.keys();
			Map<String,List<String>> graph2 = new HashMap<>();

			while(clusters.hasNext()) {
				String clusterName = clusters.next();
				JSONArray entities = json.getJSONArray(clusterName);
				List<String> clusterEntities = new ArrayList<>();
				for (int i = 0; i < entities.length(); i++) {
					clusterEntities.add(entities.getString(i));
				}
				graph2.put(clusterName, clusterEntities);
			}
			is.close();
			Files.deleteIfExists(Paths.get("temp_clusters.txt"));
			Files.deleteIfExists(Paths.get("temp_matrix.json"));
			

			Map<String,List<String>> graph1 = new HashMap<>();
			graph1 = codebase.getExpert(expertName).getClusters();



			List<String> entities = new ArrayList<>();
			for (List<String> l1 : graph1.values()) {
				for (String e1 : l1) {
					boolean inBoth = false;
					for (List<String> l2 : graph2.values()) {
						if (l2.contains(e1)) {
							inBoth = true;
							break;
						}
					}
					if (inBoth)
						entities.add(e1);
				}				
			}

			int truePositive = 0;
			int falsePositive = 0;
			int trueNegative = 0;
			int falseNegative = 0;

			for (int i = 0; i < entities.size(); i++) {
				for (int j = i+1; j < entities.size(); j++) {
					String e1 = entities.get(i);
					String e2 = entities.get(j);

					String e1ClusterG1 = "";
					String e2ClusterG1 = "";
					String e1ClusterG2 = "";
					String e2ClusterG2 = "";

					for (String cluster : graph1.keySet()) {
						if (graph1.get(cluster).contains(e1)) {
							e1ClusterG1 = cluster;
						}
						if (graph1.get(cluster).contains(e2)) {
							e2ClusterG1 = cluster;
						}
					}

					for (String cluster : graph2.keySet()) {
						if (graph2.get(cluster).contains(e1)) {
							e1ClusterG2 = cluster;
						}
						if (graph2.get(cluster).contains(e2)) {
							e2ClusterG2 = cluster;
						}
					}

					boolean sameClusterInGraph1 = false;
					if (e1ClusterG1.equals(e2ClusterG1))
						sameClusterInGraph1 = true;
					
					boolean sameClusterInGraph2 = false;
					if (e1ClusterG2.equals(e2ClusterG2))
						sameClusterInGraph2 = true;

					if (sameClusterInGraph1 && sameClusterInGraph2)
						truePositive++;
					if (sameClusterInGraph1 && !sameClusterInGraph2)
						falseNegative++;
					if (!sameClusterInGraph1 && sameClusterInGraph2)
						falsePositive++;
					if (!sameClusterInGraph1 && !sameClusterInGraph2)
						trueNegative++;
				}
			}

			float precision = (float)truePositive / (truePositive + falsePositive);
			float recall = (float)truePositive / (truePositive + falseNegative);
			fmeasure = 2*precision*recall / (precision + recall);

		} catch (Exception e) {
			e.printStackTrace();
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		return new ResponseEntity<>(fmeasure, HttpStatus.OK);
	}


	@RequestMapping(value = "/analysis", method = RequestMethod.POST)
	public ResponseEntity<AnalysisDto> getAnalysis(@PathVariable String codebaseName, @RequestBody AnalysisDto analysis) {
		logger.debug("getAnalysis");

		Codebase codebase = codebaseManager.getCodebase(codebaseName);

		Map<String,List<String>> graph1 = new HashMap<>();
		if (analysis.getDendrogramName1() == null) {
			Expert expert = codebase.getExpert(analysis.getGraphName1());
			graph1 = expert.getClusters();
		} else {
			Dendrogram dendrogram = codebase.getDendrogram(analysis.getDendrogramName1());
			Graph graph = dendrogram.getGraph(analysis.getGraphName1());
			for (Cluster c : graph.getClusters()) {
				graph1.put(c.getName(), c.getEntities());
			}
		}

		Map<String,List<String>> graph2 = new HashMap<>();
		if (analysis.getDendrogramName2() == null) {
			Expert expert = codebase.getExpert(analysis.getGraphName2());
			graph2 = expert.getClusters();
		} else {
			Dendrogram dendrogram = codebase.getDendrogram(analysis.getDendrogramName2());
			Graph graph = dendrogram.getGraph(analysis.getGraphName2());
			for (Cluster c : graph.getClusters()) {
				graph2.put(c.getName(), c.getEntities());
			}
		}

		List<String> entities = new ArrayList<>();
		for (List<String> l1 : graph1.values()) {
			for (String e1 : l1) {
				boolean inBoth = false;
				for (List<String> l2 : graph2.values()) {
					if (l2.contains(e1)) {
						inBoth = true;
						break;
					}
				}
				if (inBoth)
					entities.add(e1);
			}				
		}

		int truePositive = 0;
		int falsePositive = 0;
		int trueNegative = 0;
		int falseNegative = 0;

		for (int i = 0; i < entities.size(); i++) {
			for (int j = i+1; j < entities.size(); j++) {
				String e1 = entities.get(i);
				String e2 = entities.get(j);

				String e1ClusterG1 = "";
				String e2ClusterG1 = "";
				String e1ClusterG2 = "";
				String e2ClusterG2 = "";

				for (String cluster : graph1.keySet()) {
					if (graph1.get(cluster).contains(e1)) {
						e1ClusterG1 = cluster;
					}
					if (graph1.get(cluster).contains(e2)) {
						e2ClusterG1 = cluster;
					}
				}

				for (String cluster : graph2.keySet()) {
					if (graph2.get(cluster).contains(e1)) {
						e1ClusterG2 = cluster;
					}
					if (graph2.get(cluster).contains(e2)) {
						e2ClusterG2 = cluster;
					}
				}

				boolean sameClusterInGraph1 = false;
				if (e1ClusterG1.equals(e2ClusterG1))
					sameClusterInGraph1 = true;
				
				boolean sameClusterInGraph2 = false;
				if (e1ClusterG2.equals(e2ClusterG2))
					sameClusterInGraph2 = true;

				if (sameClusterInGraph1 && sameClusterInGraph2)
					truePositive++;
				if (sameClusterInGraph1 && !sameClusterInGraph2)
					falseNegative++;
				if (!sameClusterInGraph1 && sameClusterInGraph2)
					falsePositive++;
				if (!sameClusterInGraph1 && !sameClusterInGraph2)
					trueNegative++;

				if (sameClusterInGraph1 != sameClusterInGraph2) {
					String[] falsePair = new String[6];
					falsePair[0] = e1;
					falsePair[1] = e1ClusterG1;
					falsePair[2] = e1ClusterG2;
					falsePair[3] = e2;
					falsePair[4] = e2ClusterG1;
					falsePair[5] = e2ClusterG2;

					analysis.addFalsePair(falsePair);
				}
			}
		}

		analysis.setTruePositive(truePositive);
		analysis.setTrueNegative(trueNegative);
		analysis.setFalsePositive(falsePositive);
		analysis.setFalseNegative(falseNegative);

		float precision = (float)truePositive / (truePositive + falsePositive);
		float recall = (float)truePositive / (truePositive + falseNegative);
		float fmeasure = 2*precision*recall / (precision + recall);
		analysis.setPrecision(precision);
		analysis.setRecall(recall);
		analysis.setFmeasure(fmeasure);
		
		return new ResponseEntity<>(analysis, HttpStatus.OK);
	}


	@RequestMapping(value = "/dendrogramNames", method = RequestMethod.GET)
	public ResponseEntity<List<String>> getDendrogramNames(@PathVariable String codebaseName) {
		logger.debug("getDendrogramNames");

		return new ResponseEntity<>(codebaseManager.getCodebase(codebaseName).getDendrogramNames(), HttpStatus.OK);
	}


	@RequestMapping(value = "/dendrograms", method = RequestMethod.GET)
	public ResponseEntity<List<Dendrogram>> getDendrograms(@PathVariable String codebaseName) {
		logger.debug("getDendrograms");

		return new ResponseEntity<>(codebaseManager.getCodebase(codebaseName).getDendrograms(), HttpStatus.OK);
	}


	@RequestMapping(value = "/dendrogram/{dendrogramName}", method = RequestMethod.GET)
	public ResponseEntity<Dendrogram> getDendrogram(@PathVariable String codebaseName, @PathVariable String dendrogramName) {
		logger.debug("getDendrogram");

		return new ResponseEntity<>(codebaseManager.getCodebase(codebaseName).getDendrogram(dendrogramName), HttpStatus.OK);
	}


	@RequestMapping(value = "/dendrogram/{dendrogramName}/image", method = RequestMethod.GET)
	public ResponseEntity<byte[]> getDendrogramImage(@PathVariable String codebaseName, @PathVariable String dendrogramName) {
		logger.debug("getDendrogramImage");

		File f = new File(codebaseFolder + codebaseName + "/" + dendrogramName + ".png");
		try {
			return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(Files.readAllBytes(f.toPath()));
		} catch (IOException e) {
			e.printStackTrace();
		}
		return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
	}


	@RequestMapping(value = "/dendrogram/{dendrogramName}/delete", method = RequestMethod.DELETE)
	public ResponseEntity<HttpStatus> deleteDendrogram(@PathVariable String codebaseName, @PathVariable String dendrogramName) {
		logger.debug("deleteDendrogram");

		boolean deleted = codebaseManager.deleteDendrogram(codebaseName, dendrogramName);
		if (deleted) {
			return new ResponseEntity<>(HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
	}


	@RequestMapping(value = "/dendrogram/{dendrogramName}/controllers", method = RequestMethod.GET)
	public ResponseEntity<List<Controller>> getControllers(@PathVariable String codebaseName, @PathVariable String dendrogramName) {
		logger.debug("getControllers");
		
		return new ResponseEntity<>(codebaseManager.getCodebase(codebaseName).getDendrogram(dendrogramName).getControllers(), HttpStatus.OK);
	}


	@RequestMapping(value = "/dendrogram/{dendrogramName}/controller/{controllerName}", method = RequestMethod.GET)
	public ResponseEntity<Controller> getController(@PathVariable String codebaseName, @PathVariable String dendrogramName, @PathVariable String controllerName) {
		logger.debug("getController");

		return new ResponseEntity<>(codebaseManager.getCodebase(codebaseName).getDendrogram(dendrogramName).getController(controllerName), HttpStatus.OK);
	}


	@RequestMapping(value = "/dendrogram/create", method = RequestMethod.POST)
	public ResponseEntity<HttpStatus> createDendrogram(@RequestBody Dendrogram dendrogram) {

		logger.debug("createDendrogram");

		long startTime = System.currentTimeMillis();

		Codebase codebase = codebaseManager.getCodebase(dendrogram.getCodebaseName());

		for (String dendrogramName : codebase.getDendrogramNames()) {
			if (dendrogram.getName().toUpperCase().equals(dendrogramName.toUpperCase()))
				return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
		}
		
		try {
			Map<String,List<Pair<String,String>>> entityControllers = new HashMap<>();
			Map<String,Integer> e1e2PairCount = new HashMap<>();
			JSONArray similarityMatrix = new JSONArray();
			JSONObject dendrogramData = new JSONObject();


			//read datafile
			InputStream is = new FileInputStream(codebaseFolder + codebase.getName() + ".txt");
			JSONObject datafileJSON = new JSONObject(IOUtils.toString(is, "UTF-8"));
			is.close();

			for (String profile : dendrogram.getProfiles()) {
				for (String controllerName : codebase.getProfile(profile)) {
					Controller controller = new Controller(controllerName);
					dendrogram.addController(controller);

					JSONArray entities = datafileJSON.getJSONArray(controllerName);
					for (int i = 0; i < entities.length(); i++) {
						JSONArray entityArray = entities.getJSONArray(i);
						String entity = entityArray.getString(0);
						String mode = entityArray.getString(1);
						
						controller.addEntity(entity, mode);
						controller.addEntitySeq(entity, mode);

						if (!dendrogram.containsEntity(entity))
							dendrogram.addEntity(new Entity(entity));

						if (entityControllers.containsKey(entity)) {
							boolean containsController = false;
							for (Pair<String,String> controllerPair : entityControllers.get(entity)) {
								if (controllerPair.getFirst().equals(controllerName)) {
									containsController = true;
									if (!controllerPair.getSecond().contains(mode))
										controllerPair.setSecond("RW");
									break;
								}
							}
							if (!containsController) {
								entityControllers.get(entity).add(new Pair<String,String>(controllerName,mode));
							}
						} else {
							List<Pair<String,String>> controllersPairs = new ArrayList<>();
							controllersPairs.add(new Pair<String,String>(controllerName,mode));
							entityControllers.put(entity, controllersPairs);
						}

						if (i < entities.length() - 1) {
							JSONArray nextEntityArray = entities.getJSONArray(i+1);
							String nextEntity = nextEntityArray.getString(0);

							if (!entity.equals(nextEntity)) {
								String e1e2 = entity + "->" + nextEntity;
								String e2e1 = nextEntity + "->" + entity;

								if (e1e2PairCount.containsKey(e1e2)) {
									e1e2PairCount.put(e1e2, e1e2PairCount.get(e1e2) + 1);
								} else {
									int count = e1e2PairCount.containsKey(e2e1) ? e1e2PairCount.get(e2e1) : 0;
									e1e2PairCount.put(e2e1, count + 1);
								}
							}
						}
					}
				}
			}

			List<String> entitiesList = new ArrayList<String>(entityControllers.keySet());
			Collections.sort(entitiesList);

			int maxNumberOfPairs = Collections.max(e1e2PairCount.values());

			JSONArray seq1SimilarityMatrix = new JSONArray();
			JSONArray seq2SimilarityMatrix = new JSONArray();
			JSONArray seq3SimilarityMatrix = new JSONArray();
			for (int i = 0; i < entitiesList.size(); i++) {
				String e1 = entitiesList.get(i);
				JSONArray seq1MatrixAux = new JSONArray();
				JSONArray seq2MatrixAux = new JSONArray();
				JSONArray seq3MatrixAux = new JSONArray();
				for (int j = 0; j < entitiesList.size(); j++) {
					String e2 = entitiesList.get(j);
					if (e1.equals(e2)) {
						seq1MatrixAux.put(new Float(maxNumberOfPairs));
						seq2MatrixAux.put(new Float(1));
						seq3MatrixAux.put(new Float(1));
					} else {
						String e1e2 = e1 + "->" + e2;
						String e2e1 = e2 + "->" + e1;
						float e1e2Count = e1e2PairCount.containsKey(e1e2) ? e1e2PairCount.get(e1e2) : 0;
						float e2e1Count = e1e2PairCount.containsKey(e2e1) ? e1e2PairCount.get(e2e1) : 0;

						seq1MatrixAux.put(new Float(e1e2Count + e2e1Count));
						seq2MatrixAux.put(new Float(e1e2Count + e2e1Count));
						seq3MatrixAux.put(new Float(e1e2Count + e2e1Count));
					}
				}

				List<Float> seq3List = new ArrayList<>();
				for (int k = 0; k < seq3MatrixAux.length(); k++) {
					seq3List.add((float)seq3MatrixAux.get(k));
				}

				float seq3Max = Collections.max(seq3List);

				for (int j = 0; j < entitiesList.size(); j++) {
					if (!entitiesList.get(j).equals(e1)) {
						seq2MatrixAux.put(j, new Float(((float)seq2MatrixAux.get(j)) / maxNumberOfPairs));
						seq3MatrixAux.put(j, new Float(((float)seq3MatrixAux.get(j)) / seq3Max));
					}
				}

				seq1SimilarityMatrix.put(seq1MatrixAux);
				seq2SimilarityMatrix.put(seq2MatrixAux);
				seq3SimilarityMatrix.put(seq3MatrixAux);
			}

			for (int i = 0; i < entitiesList.size(); i++) {
				String e1 = entitiesList.get(i);
				JSONArray matrixAux = new JSONArray();
				for (int j = 0; j < entitiesList.size(); j++) {
					String e2 = entitiesList.get(j);
					float inCommon = 0;
					float inCommonW = 0;
					float inCommonR = 0;
					float e1ControllersW = 0;
					float e1ControllersR = 0;
					for (Pair<String,String> p1 : entityControllers.get(e1)) {
						for (Pair<String,String> p2 : entityControllers.get(e2)) {
							if (p1.getFirst().equals(p2.getFirst()))
								inCommon++;
							if (p1.getFirst().equals(p2.getFirst()) && p1.getSecond().contains("W") && p2.getSecond().contains("W"))
								inCommonW++;
							if (p1.getFirst().equals(p2.getFirst()) && p1.getSecond().equals("R") && p2.getSecond().equals("R"))
								inCommonR++;
						}
						if (p1.getSecond().contains("W"))
							e1ControllersW++;
						if (p1.getSecond().equals("R"))
							e1ControllersR++;
					}

					float accessMetric = inCommon / entityControllers.get(e1).size();
					float writeMetric = e1ControllersW == 0 ? 0 : inCommonW / e1ControllersW;
					float readMetric = e1ControllersR == 0 ? 0 : inCommonR / e1ControllersR;
					float sequence1Metric = (float) seq1SimilarityMatrix.getJSONArray(i).get(j);
					float sequence2Metric = (float) seq2SimilarityMatrix.getJSONArray(i).get(j);
					float sequence3Metric = (float) seq3SimilarityMatrix.getJSONArray(i).get(j);
					float metric = accessMetric * dendrogram.getAccessMetricWeight() / 100 + 
									writeMetric * dendrogram.getWriteMetricWeight() / 100 +
									readMetric * dendrogram.getReadMetricWeight() / 100 +
									sequence1Metric * dendrogram.getSequenceMetric1Weight() / 100 +
									sequence2Metric * dendrogram.getSequenceMetric2Weight() / 100 +
									sequence3Metric * dendrogram.getSequenceMetric3Weight() / 100;
					matrixAux.put(metric);
				}
				similarityMatrix.put(matrixAux);

				float immutability = 0;
				for (Pair<String,String> controllerPair : entityControllers.get(e1)) {
					if (controllerPair.getSecond().equals("R"))
						immutability++;
				}
				dendrogram.getEntity(e1).setImmutability(immutability / entityControllers.get(e1).size());
			}
			dendrogramData.put("matrix", similarityMatrix);
			dendrogramData.put("entities", entitiesList);

			try (FileWriter file = new FileWriter(codebaseFolder + codebase.getName() + "/" + dendrogram.getName() + ".txt")){
				file.write(dendrogramData.toString());
			} catch (IOException e) {
				e.printStackTrace();
			}

			codebase.addDendrogram(dendrogram);
			codebaseManager.writeCodebase(codebase.getName(), codebase);

			// run python script with clustering algorithm
			Runtime r = Runtime.getRuntime();
			String pythonScriptPath = resourcesPath + "dendrogram.py";
			String[] cmd = new String[6];
			cmd[0] = PYTHON;
			cmd[1] = pythonScriptPath;
			cmd[2] = codebaseFolder;
			cmd[3] = codebase.getName();
			cmd[4] = dendrogram.getName();
			cmd[5] = dendrogram.getLinkageType();
			
			Process p = r.exec(cmd);

			p.waitFor();

			BufferedReader bre = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String line;
			while ((line = bre.readLine()) != null) {
				System.out.println("Inside Elapsed time: " + line + " seconds");
			}
		} catch (Exception e) {
			e.printStackTrace();
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}
		
		long elapsedTimeMillis = System.currentTimeMillis() - startTime;
		float elapsedTimeSec = elapsedTimeMillis/1000F;
		System.out.println("Complete. Elapsed time: " + elapsedTimeSec + " seconds");

		return new ResponseEntity<>(HttpStatus.CREATED);
	}


	@RequestMapping(value = "/dendrogram/{dendrogramName}/cut", method = RequestMethod.POST)
	public ResponseEntity<HttpStatus> cutDendrogram(@PathVariable String codebaseName, @PathVariable String dendrogramName, @RequestBody Graph graph) {
		logger.debug("cutDendrogram");

		try {
			Codebase codebase = codebaseManager.getCodebase(codebaseName);
			Dendrogram dendrogram = codebase.getDendrogram(dendrogramName);

			Runtime r = Runtime.getRuntime();
			String pythonScriptPath = resourcesPath + "cutDendrogram.py";
			String[] cmd = new String[8];
			cmd[0] = PYTHON;
			cmd[1] = pythonScriptPath;
			cmd[2] = codebaseFolder;
			cmd[3] = codebaseName;
			cmd[4] = dendrogramName;
			cmd[5] = dendrogram.getLinkageType();
			cmd[6] = Float.toString(graph.getCutValue());
			cmd[7] = graph.getCutType();
			Process p = r.exec(cmd);

			p.waitFor();

			BufferedReader bre = new BufferedReader(new InputStreamReader(p.getInputStream()));
			float silhouetteScore = Float.parseFloat(bre.readLine());
			graph.setSilhouetteScore(silhouetteScore);

			String cutValue = new Float(graph.getCutValue()).toString().replaceAll("\\.?0*$", "");
			if (dendrogram.getGraphsNames().contains(graph.getCutType() + cutValue)) {
				int i = 2;
				while (dendrogram.getGraphsNames().contains(graph.getCutType() + cutValue + "(" + i + ")")) {
					i++;
				}
				graph.setName(graph.getCutType() + cutValue + "(" + i + ")");
			} else {
				graph.setName(graph.getCutType() + cutValue);
			}

			InputStream is = new FileInputStream("temp_clusters.txt");
			JSONObject json = new JSONObject(IOUtils.toString(is, "UTF-8"));

			Iterator<String> clusters = json.sortedKeys();
			ArrayList<Integer> clusterIds = new ArrayList<>();

			while(clusters.hasNext()) {
				clusterIds.add(Integer.parseInt(clusters.next()));
			}
			Collections.sort(clusterIds);
			for (Integer id : clusterIds) {
				String clusterId = String.valueOf(id);
				JSONArray entities = json.getJSONArray(clusterId);
				Cluster cluster = new Cluster("Cluster" + clusterId);
				for (int i = 0; i < entities.length(); i++) {
					String entity = entities.getString(i);
					cluster.addEntity(entity);
				}
				graph.addCluster(cluster);
			}
			is.close();
			Files.deleteIfExists(Paths.get("temp_clusters.txt"));
			dendrogram.addGraph(graph);

			codebaseManager.writeCodebase(codebaseName, codebase);
		} catch (Exception e) {
			e.printStackTrace();
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}
}