package com.example.restservice;

import sepses.ondemand_extractor.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GreetingController {

	@GetMapping("/startservice")
	public StartService startservice(@RequestParam(name = "query") String query) throws Exception {
		return new StartService	(query);
	}
	
	
}