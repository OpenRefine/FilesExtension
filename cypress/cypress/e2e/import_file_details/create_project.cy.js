describe(__filename, function () {
  afterEach(() => {
    cy.addProjectForDeletion();
  });

  it('Test the create project using Files Extension', function () {
    cy.visitOpenRefine();
    cy.navigateTo('Create project');
    cy.get('#create-project-ui-source-selection-tabs > a')
      .contains('Files from local directory')
      .click();
    // enter a category name
    cy.get('#drive-selector'
    ).select('/home');


    // Wait for the directory tree container to become visible
    cy.get('#directory-tree-container', { timeout: 10000 }) // Adjust timeout as needed
      .should('be.visible'); // Ensures the element is visible

    // Interact with the "Home" option in the tree
    cy.get('#directory-tree').contains('home').click(); // Replace 'Home' with exact text if needed


    cy.get(
       '.create-project-ui-source-selection-tab-body.selected button.button-primary'
    )
      .contains('Next')
      .click();

    // then ensure we are on the preview page
    cy.get('.create-project-ui-panel').contains('Project name');

    // preview and click next
    cy.get('button[bind="createProjectButton"]')
      .contains('Create Project »')
      .click();
    cy.waitForProjectTable();
  });
});
