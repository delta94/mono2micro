package pt.ist.socialsoftware.mono2micro.controller;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import pt.ist.socialsoftware.mono2micro.domain.Dendrogram;
import pt.ist.socialsoftware.mono2micro.domain.Graph;

@RestController
@RequestMapping(value = "/mono2micro/")
public class Mono2MicroController {
	private static Logger logger = LoggerFactory.getLogger(Mono2MicroController.class);

	@RequestMapping(method = RequestMethod.GET)
	public ResponseEntity<String[]> getGraphs() {
		logger.debug("getGraphs");

		String[] graphs = new String[3];
		for (int i = 0; i < 3; i++) {
			graphs[i] = "Graph" + i;
		}

		return new ResponseEntity<>(graphs, HttpStatus.OK);
	}

	@RequestMapping(value = "/{name}", method = RequestMethod.GET)
	public ResponseEntity<Graph> getGraph(@PathVariable("name") String name) {
		logger.debug("getGraph");

		Graph graph = new Graph(name);

		return new ResponseEntity<>(graph, HttpStatus.OK);
	}

	@RequestMapping(value = "/load", method = RequestMethod.POST)
	public ResponseEntity<Graph> loadGraph(@RequestBody Graph graph) {
		logger.debug("loadGraph name:{}", graph.getName());

		// TO DO:

		return new ResponseEntity<>(graph, HttpStatus.CREATED);
	}

	@RequestMapping(value = "/{name}", method = RequestMethod.DELETE)
	public ResponseEntity<String> deleteGraph(@PathVariable("name") String name) {
		logger.debug("deleteGraph name:{}", name);

		// TO DO:

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequestMapping(value = "/createDendrogram", method = RequestMethod.POST)
	public ResponseEntity<Dendrogram> createDendrogram(@RequestParam("file") MultipartFile datafile) {
		logger.debug("createDendrogram filename: {}", datafile.getOriginalFilename());
		
		BufferedReader br;
		List<String> datafileLines = new ArrayList<>();
		try {
     		String line;
     		InputStream is = datafile.getInputStream();
     		br = new BufferedReader(new InputStreamReader(is));
     		while ((line = br.readLine()) != null)
				datafileLines.add(line);
		} catch (IOException e) {
			System.err.println(e.getMessage());
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);     
		}

		for (String line : datafileLines){
			//TO DO: read file lines and create Dendrogram DTO
		}

		return new ResponseEntity<>(HttpStatus.CREATED);
     }
}