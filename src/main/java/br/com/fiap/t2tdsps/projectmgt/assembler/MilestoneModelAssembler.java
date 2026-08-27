package br.com.fiap.t2tdsps.projectmgt.assembler;

import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import br.com.fiap.t2tdsps.projectmgt.controller.MilestoneController;
import br.com.fiap.t2tdsps.projectmgt.model.Milestone;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class MilestoneModelAssembler implements RepresentationModelAssembler< Milestone, EntityModel<Milestone> >{

	@Override
	public EntityModel<Milestone> toModel(Milestone entity) {
		EntityModel<Milestone> model = EntityModel.of(entity);
		model.add(linkTo(methodOn(MilestoneController.class).findById(entity.getId())).withSelfRel());
		model.add(linkTo(methodOn(MilestoneController.class).findAll()).withRel("findAll"));
		model.add(linkTo(methodOn(MilestoneController.class).delete(entity.getId())).withRel("delete"));
		
		return model;
	}

}
