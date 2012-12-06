#!/usr/bin/env ruby -wKU
# encoding: UTF-8

merged_file = File.open("rfc-all.txt", "w:UTF-8")
Dir.entries(".").each do |file|
  if (file != "rfc-all.txt" and File.extname(file) == ".txt")
    merged_file.print "#{ file }\t"
    File.open(file, "r:UTF-8").each do |line|
      begin
        merged_file.print line.chomp.delete "\t"
      rescue
        puts file.inspect
      end
    end
    merged_file.print "\n"
  end
end
