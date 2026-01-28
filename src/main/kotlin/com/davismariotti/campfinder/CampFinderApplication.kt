package com.davismariotti.campfinder

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class CampFinderApplication

fun main(args: Array<String>) {
    runApplication<CampFinderApplication>(*args)
}
