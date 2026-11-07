package dev.caseintake;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = {
        "http://localhost:5173",
        "http://localhost:5174",
        "http://localhost:5175",
        "http://localhost:5176"
})
public class DocumentController {
 private final ObjectMapper mapper = new ObjectMapper();
 private JsonNode json(Path path){try {return mapper.readTree(Files.readString(path));}catch(IOException e){throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Missing file: "+path.getFileName());}}
 @GetMapping("/catalog") public JsonNode catalog(){return json(Path.of("catalog/field_catalog.json"));}
 @GetMapping("/documents") public List<Map<String,Object>> list() throws IOException {
  List<Map<String,Object>> result=new ArrayList<>();
  try(var stream=Files.list(Path.of("documents"))){for(Path p:stream.filter(x->x.toString().endsWith(".txt")).sorted().toList()){
   String id=p.getFileName().toString().replace(".txt",""); Path output=Path.of("out",id+".json");
   JsonNode extracted=Files.exists(output)?json(output):null;
   result.add(Map.of("id",id,"title",Files.readString(p).lines().findFirst().orElse(id),"caseCount",extracted==null?0:extracted.path("cases").size(),"flags",extracted==null?List.of("not_processed"):extracted.path("flags")));
  }}return result;
 }
 @GetMapping("/documents/{id}") public JsonNode one(@PathVariable String id) throws IOException {
  if(!id.matches("D[0-9]{2}"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid document ID");
  Path text=Path.of("documents",id+".txt");if(!Files.exists(text))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Missing document");
  ObjectNode result=mapper.createObjectNode();result.put("id",id);result.put("text",Files.readString(text));
  Path out=Path.of("out",id+".json");result.set("extraction",Files.exists(out)?json(out):NullNode.getInstance());return result;
 }
}
