package com.thiago.spacebooking;

import org.springframework.boot.SpringApplication;

public class TestSpaceBookingApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(SpaceBookingApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
