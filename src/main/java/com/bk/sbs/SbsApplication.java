package com.bk.sbs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SpringBootApplication
public class SbsApplication {

	private static final Logger log = LoggerFactory.getLogger(SbsApplication.class);

	public static void main(String[] args) {
		log.info("[임시로그] 서버 프로세스 시작(main 진입): 시각={}", java.time.Instant.now());
		SpringApplication.run(SbsApplication.class, args);
	}

}
