package com.hasu.tilelayout.viewmodel

import com.hasu.tilelayout.db.ProjectRepository
import com.hasu.tilelayout.models.Project
import kotlin.test.*

class ProjectListViewModelTest {

    private class FakeProjectRepository : ProjectRepository {
        private val projects = mutableListOf<Project>()

        override suspend fun getAll(): List<Project> = projects.toList()
        override suspend fun getById(id: String): Project? = projects.find { it.id == id }
        override suspend fun insert(project: Project) {
            projects.add(project)
        }
        override suspend fun delete(id: String) {
            projects.removeAll { it.id == id }
        }
    }

    private lateinit var repo: FakeProjectRepository
    private lateinit var vm: ProjectListViewModel

    // Called before each test
    private fun setUp() {
        repo = FakeProjectRepository()
        vm = ProjectListViewModel(repo)
    }

    @Test
    fun loadEmitsProjectsFromRepo() = kotlinx.coroutines.runBlocking {
        // given: repo has 2 projects
        setUp()
        repo.insert(Project(id = "p1", name = "Kitchen"))
        repo.insert(Project(id = "p2", name = "Bathroom"))

        // when: load is called
        vm.load()

        // want: StateFlow emits both projects
        val projects = vm.projects.value
        assertEquals(2, projects.size)
        assertEquals("Kitchen", projects[0].name)
        assertEquals("Bathroom", projects[1].name)
    }

    @Test
    fun createAddsProjectAndReloads() = kotlinx.coroutines.runBlocking {
        // given: empty repo
        setUp()

        // when: create new project
        val created = vm.create("Living Room")

        // want: project returned with correct name
        assertEquals("Living Room", created.name)
        assertTrue(created.id.isNotEmpty())

        // want: StateFlow now contains the project
        assertEquals(1, vm.projects.value.size)
        assertEquals("Living Room", vm.projects.value[0].name)
    }

    @Test
    fun deleteRemovesProjectAndReloads() = kotlinx.coroutines.runBlocking {
        // given: repo has a project
        setUp()
        repo.insert(Project(id = "p1", name = "Kitchen"))
        vm.load()
        assertEquals(1, vm.projects.value.size)

        // when: delete the project
        vm.delete("p1")

        // want: StateFlow is empty
        assertEquals(0, vm.projects.value.size)
    }

    @Test
    fun initialProjectsIsEmpty() {
        // given: fresh ViewModel
        setUp()

        // want: initial state is empty list
        assertTrue(vm.projects.value.isEmpty())
    }
}
