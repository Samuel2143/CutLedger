package com.cutledger.interpretation;
import com.cutledger.workspace.SessionService.Session;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1")
public class InterpretationController {
    private final InterpretationService service;
    public InterpretationController(InterpretationService service){this.service=service;}
    @PostMapping("/campaigns/{id}/interpretations") @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
    Object create(@PathVariable UUID id,@RequestAttribute("workspaceSession") Session session,@RequestHeader("Idempotency-Key") UUID key,@Valid @RequestBody InterpretationService.Input input){return service.create(session,id,key,input);}
    @GetMapping("/interpretations/{id}") Object view(@PathVariable UUID id,@RequestAttribute("workspaceSession") Session session){return service.view(session,id);}
}
