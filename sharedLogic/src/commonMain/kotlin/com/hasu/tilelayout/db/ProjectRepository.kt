package com.hasu.tilelayout.db

import com.hasu.tilelayout.models.Project
import com.hasu.tilelayout.models.UnitSystem

interface ProjectRepository {
    suspend fun getAll(): List<Project>
    suspend fun getById(id: String): Project?
    suspend fun insert(project: Project)
    suspend fun delete(id: String)
}

class SqlDelightProjectRepository(private val queries: TileLayoutDbQueries) : ProjectRepository {

    override suspend fun getAll(): List<Project> {
        return queries.getProjects().executeAsList().map { it.toProject() }
    }

    override suspend fun getById(id: String): Project? {
        return queries.getProjectById(id).executeAsOneOrNull()?.toProject()
    }

    override suspend fun insert(project: Project) {
        queries.insertProject(
            id = project.id,
            name = project.name,
            units = project.units.name,
            created_at = project.createdAt,
        )
    }

    override suspend fun delete(id: String) {
        queries.deleteProject(id)
    }
}

internal fun Projects.toProject(): Project = Project(
    id = id,
    name = name,
    units = try {
        UnitSystem.valueOf(units)
    } catch (_: IllegalArgumentException) {
        UnitSystem.MM
    },
    createdAt = created_at,
)
