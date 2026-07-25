package com.example.bot.spring.echo;

import java.io.BufferedReader;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.bot.spring.game.CreatVillage;

/** HTTP adapter for the special-village creation form. */
@RestController
public class SpecialVillageController {
  private final ObjectMapper objectMapper = new ObjectMapper();

  @RequestMapping("/specialvillage")
  @CrossOrigin
  public ResponseEntity<Map<String, String>> create(HttpServletRequest request) {
    try {
      StringBuilder body = new StringBuilder();
      try (BufferedReader reader = request.getReader()) {
        String line;
        while ((line = reader.readLine()) != null) {
          body.append(line);
        }
      }

      Map<String, Object> payload = objectMapper.readValue(
          body.toString(), new TypeReference<Map<String, Object>>() { });
      @SuppressWarnings("unchecked")
      List<String> messages = (List<String>) payload.get("message");
      if (messages == null) {
        return ResponseEntity.badRequest().build();
      }

      int villageNumber = new CreatVillage().createNewVillage(messages);
      return ResponseEntity.ok(java.util.Collections.singletonMap(
          "data", String.valueOf(villageNumber)));
    } catch (Exception e) {
      return ResponseEntity.badRequest().build();
    }
  }
}
