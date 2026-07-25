package com.example.bot.spring.echo;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.springframework.http.HttpStatus;

public class MainControllerTest {
  @Test
  public void missingApiParametersAreRejected() {
    MainController controller = new MainController();

    assertEquals(HttpStatus.BAD_REQUEST, controller.index(null, "user").getStatusCode());
    assertEquals(HttpStatus.BAD_REQUEST, controller.index("1234", null).getStatusCode());
  }
}
