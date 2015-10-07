'use strict';

var src = 'ProductApi.js';

module.exports = function(grunt) {
  grunt.initConfig({
    pkg: grunt.file.readJSON('package.json'),
    jshint: {
      options: {
        globals: {
          XDomainRequest: true,
          window: true
        },
        jshintrc: true
      },
      src: [src, 'Gruntfile.js']
    },
    watch: {
      files: [src, 'Gruntfile.js', 'test/*.js', '!**/node_modules/**/*'],
      tasks: ['jshint', 'mochacli']
    },
    mochacli: {
      options: {
        bail: true
      },
      all: ['test/*.js']
    }
  });

  grunt.loadNpmTasks('grunt-contrib-jshint');
  grunt.loadNpmTasks('grunt-contrib-watch');
  grunt.loadNpmTasks('grunt-mocha-cli');

  grunt.registerTask('default', ['test']);

  grunt.registerTask('test', [
    'jshint',
    'mochacli'
  ]);

  grunt.registerTask('develop', [
    'test',
    'watch'
  ]);
};
