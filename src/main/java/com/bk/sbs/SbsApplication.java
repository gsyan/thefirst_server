package com.bk.sbs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SbsApplication {

	public static void main(String[] args) {
		SpringApplication.run(SbsApplication.class, args);
	}

}
