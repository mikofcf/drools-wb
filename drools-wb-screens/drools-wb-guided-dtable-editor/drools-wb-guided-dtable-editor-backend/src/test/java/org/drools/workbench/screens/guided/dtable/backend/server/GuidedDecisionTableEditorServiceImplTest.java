/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.workbench.screens.guided.dtable.backend.server;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.enterprise.event.Event;

import org.drools.workbench.models.datamodel.oracle.PackageDataModelOracle;
import org.drools.workbench.models.datamodel.workitems.PortableWorkDefinition;
import org.drools.workbench.models.guided.dtable.shared.model.GuidedDecisionTable52;
import org.drools.workbench.screens.guided.dtable.model.GuidedDecisionTableEditorContent;
import org.drools.workbench.screens.guided.dtable.type.GuidedDTableResourceTypeDefinition;
import org.drools.workbench.screens.workitems.service.WorkItemsEditorService;
import org.guvnor.common.services.backend.metadata.MetadataServerSideService;
import org.guvnor.common.services.backend.util.CommentedOptionFactory;
import org.guvnor.common.services.backend.validation.GenericValidator;
import org.guvnor.common.services.shared.metadata.model.Metadata;
import org.guvnor.common.services.shared.metadata.model.Overview;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kie.workbench.common.services.backend.source.SourceService;
import org.kie.workbench.common.services.backend.source.SourceServices;
import org.kie.workbench.common.services.datamodel.backend.server.service.DataModelService;
import org.kie.workbench.common.services.shared.project.KieProjectService;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.uberfire.backend.vfs.Path;
import org.uberfire.backend.vfs.PathFactory;
import org.uberfire.ext.editor.commons.service.CopyService;
import org.uberfire.ext.editor.commons.service.DeleteService;
import org.uberfire.ext.editor.commons.service.RenameService;
import org.uberfire.io.IOService;
import org.uberfire.java.nio.base.options.CommentedOption;
import org.uberfire.mocks.EventSourceMock;
import org.uberfire.rpc.SessionInfo;
import org.uberfire.workbench.events.ResourceOpenedEvent;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class GuidedDecisionTableEditorServiceImplTest {

    @Mock
    private IOService ioService;

    @Mock
    private CopyService copyService;

    @Mock
    private DeleteService deleteService;

    @Mock
    private RenameService renameService;

    @Mock
    private DataModelService dataModelService;

    @Mock
    private WorkItemsEditorService workItemsService;

    @Mock
    private KieProjectService projectService;

    @Mock
    private Event<ResourceOpenedEvent> resourceOpenedEvent = new EventSourceMock<>();

    @Mock
    private GenericValidator genericValidator;

    @Mock
    private CommentedOptionFactory commentedOptionFactory;

    @Mock
    private SourceServices mockSourceServices;

    @Mock
    private MetadataServerSideService mockMetaDataService;

    @Mock
    private SessionInfo sessionInfo;

    @Mock
    private org.guvnor.common.services.project.model.Package pkg;

    private GuidedDTableResourceTypeDefinition type = new GuidedDTableResourceTypeDefinition();
    private GuidedDecisionTableEditorServiceImpl service;

    @Before
    public void setup() {
        service = new GuidedDecisionTableEditorServiceImpl( ioService,
                                                            copyService,
                                                            deleteService,
                                                            renameService,
                                                            dataModelService,
                                                            workItemsService,
                                                            projectService,
                                                            resourceOpenedEvent,
                                                            genericValidator,
                                                            commentedOptionFactory,
                                                            sessionInfo ) {
            {
                this.sourceServices = mockSourceServices;
                this.metadataService = mockMetaDataService;
            }
        };

        when( projectService.resolvePackage( any( Path.class ) ) ).thenReturn( pkg );
        when( pkg.getPackageName() ).thenReturn( "mypackage" );
        when( pkg.getPackageMainResourcesPath() ).thenReturn( PathFactory.newPath( "mypackage",
                                                                                   "default://project/src/main/resources" ) );

    }

    @Test
    public void checkCreate() {
        final Path context = mock( Path.class );
        final String fileName = "filename." + type.getSuffix();
        final GuidedDecisionTable52 content = new GuidedDecisionTable52();
        final String comment = "comment";

        when( context.toURI() ).thenReturn( "default://project/src/main/resources/mypackage" );

        final Path p = service.create( context,
                                       fileName,
                                       content,
                                       comment );

        verify( ioService,
                times( 1 ) ).write( any( org.uberfire.java.nio.file.Path.class ),
                                    any( String.class ),
                                    any( CommentedOption.class ) );

        assertTrue( p.toURI().contains( "src/main/resources/mypackage/filename." + type.getSuffix() ) );
        assertEquals( "mypackage",
                      content.getPackageName() );
    }

    @Test
    public void checkLoad() {
        final Path path = mock( Path.class );
        when( path.toURI() ).thenReturn( "default://project/src/main/resources/mypackage/dtable.gdst" );

        when( ioService.readAllString( any( org.uberfire.java.nio.file.Path.class ) ) ).thenReturn( "" );

        final GuidedDecisionTable52 model = service.load( path );

        verify( ioService,
                times( 1 ) ).readAllString( any( org.uberfire.java.nio.file.Path.class ) );
        assertNotNull( model );
    }

    @Test
    public void checkConstructContent() {
        final Path path = mock( Path.class );
        final Overview overview = mock( Overview.class );
        final PackageDataModelOracle oracle = mock( PackageDataModelOracle.class );
        final Set<PortableWorkDefinition> workItemDefinitions = new HashSet<>();
        when( path.toURI() ).thenReturn( "default://project/src/main/resources/mypackage/dtable.gdst" );
        when( dataModelService.getDataModel( eq( path ) ) ).thenReturn( oracle );
        when( workItemsService.loadWorkItemDefinitions( eq( path ) ) ).thenReturn( workItemDefinitions );

        final GuidedDecisionTableEditorContent content = service.constructContent( path,
                                                                                   overview );

        verify( resourceOpenedEvent,
                times( 1 ) ).fire( any( ResourceOpenedEvent.class ) );

        assertNotNull( content.getModel() );
        assertNotNull( content.getDataModel() );
        assertNotNull( content.getWorkItemDefinitions() );
        assertEquals( overview,
                      content.getOverview() );
    }

    @Test
    @SuppressWarnings("unchecked")
    public void checkSave() {
        final Path path = mock( Path.class );
        final GuidedDecisionTable52 model = new GuidedDecisionTable52();
        final Metadata metadata = mock( Metadata.class );
        final String comment = "comment";
        when( path.toURI() ).thenReturn( "default://project/src/main/resources/mypackage/dtable.gdst" );

        service.save( path,
                      model,
                      metadata,
                      comment );

        verify( ioService,
                times( 1 ) ).write( any( org.uberfire.java.nio.file.Path.class ),
                                    any( String.class ),
                                    any( Map.class ),
                                    any( CommentedOption.class ) );

        assertEquals( "mypackage",
                      model.getPackageName() );
    }

    @Test
    public void checkDelete() {
        final Path path = mock( Path.class );
        final String comment = "comment";

        service.delete( path,
                        comment );

        verify( deleteService,
                times( 1 ) ).delete( eq( path ),
                                     eq( comment ) );
    }

    @Test
    public void checkRename() {
        final Path path = mock( Path.class );
        final String newFileName = "newFileName";
        final String comment = "comment";

        service.rename( path,
                        newFileName,
                        comment );

        verify( renameService,
                times( 1 ) ).rename( eq( path ),
                                     eq( newFileName ),
                                     eq( comment ) );
    }

    @Test
    public void checkCopy() {
        final Path path = mock( Path.class );
        final String newFileName = "newFileName";
        final String comment = "comment";

        service.copy( path,
                      newFileName,
                      comment );

        verify( copyService,
                times( 1 ) ).copy( eq( path ),
                                   eq( newFileName ),
                                   eq( comment ) );
    }

    @Test
    public void copyCopyToPackage() {
        final Path path = mock( Path.class );
        final String newFileName = "newFileName";
        final Path newPackagePath = mock( Path.class );
        final String comment = "comment";

        service.copy( path,
                      newFileName,
                      newPackagePath,
                      comment );

        verify( copyService,
                times( 1 ) ).copy( eq( path ),
                                   eq( newFileName ),
                                   eq( newPackagePath ),
                                   eq( comment ) );
    }

    @Test
    @SuppressWarnings("unchecked")
    public void checkToSource() {
        final Path path = mock( Path.class );
        final GuidedDecisionTable52 model = new GuidedDecisionTable52();
        final SourceService mockSourceService = mock( SourceService.class );

        when( path.toURI() ).thenReturn( "default://project/src/main/resources/mypackage" );
        when( mockSourceServices.getServiceFor( any( org.uberfire.java.nio.file.Path.class ) ) ).thenReturn( mockSourceService );

        service.toSource( path,
                          model );

        verify( mockSourceServices,
                times( 1 ) ).getServiceFor( any( org.uberfire.java.nio.file.Path.class ) );
        verify( mockSourceService,
                times( 1 ) ).getSource( any( org.uberfire.java.nio.file.Path.class ),
                                        eq( model ) );
    }

    @Test
    public void checkValidate() {
//        service.validate(  )
    }

}
