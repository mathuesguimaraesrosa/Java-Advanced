package br.com.fiap.t2tdsps.projectmgt.assembler;

import br.com.fiap.t2tdsps.projectmgt.controller.MilestoneController;
import br.com.fiap.t2tdsps.projectmgt.dto.MilestoneResponse;
import br.com.fiap.t2tdsps.projectmgt.mapper.MilestoneMapper;
import br.com.fiap.t2tdsps.projectmgt.model.Milestone;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class MilestoneModelAssembler implements RepresentationModelAssembler<Milestone, EntityModel<MilestoneResponse>> {

    @Override
    public EntityModel<MilestoneResponse> toModel(Milestone milestone) {

        EntityModel<MilestoneResponse> model = EntityModel.of(MilestoneMapper.toResponse(milestone));
        model.add(linkTo(methodOn(MilestoneController.class).findById(milestone.getId())).withSelfRel());
        // findAll saiu do link (Semana 3, Exercício 2): agora é paginado (PageResponse), não faz mais
        // parte da navegação HATEOAS - methodOn exige os mesmos parâmetros do método real, e não faz
        // sentido fixar page/sizePage aqui.
        model.add(linkTo(methodOn(MilestoneController.class).delete(milestone.getId())).withRel("delete"));

        return model;
    }
}
