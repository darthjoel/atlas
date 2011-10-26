require 'java'
require 'open-uri'

module Atlas

  def self.stringify h
    h.inject(Hash.new) do |a, (k, v)|
      a[k.to_s] = v.to_s
      a
    end
  end

  def self.parse_system path, name="__ROOT__"
    RootSystemParser.new(name, path).__parse
  end

  def self.parse_install_list xs

    # handle case of ["uri:stuff", { :param => 7}] is single uri
    _, p =  Array(xs)
    if p and p.is_a? Hash
      xs = [xs]
    end

    Array(xs).map do |x|
      i, params = Array(x)
      params    = if params then
                    Atlas.stringify params
                  else
                    Hash.new
                  end
      com.ning.atlas.spi.Uri.value_of2(i, params)
    end
  end

  def self.parse_env path
    RootEnvParser.new(path).__parse
  end


  #
  # Environment Parsing Stuff
  #

  class RootEnvParser
    def initialize path
      @path     = path
      @template = open(path).read
    end

    def __parse
      eval @template, binding, @path, 1
      @env
    end

    def environment name="environment", &block
      # name is unused, is there for pretty readability
      @env = EnvironmentParser.new(block).__parse
    end
  end

  class EnvironmentParser

    def initialize block
      @block                                          = block
      @properties, @provisioners, @installers, @bases = {}, {}, {}, {}
    end

    def __parse
      instance_eval &@block
      com.ning.atlas.Environment.new @provisioners,
                                     @installers,
                                     @bases,
                                     @properties
    end

    def base name, args={}
      p, params = Array(args[:provisioner])
      raise "Provisioner URI is required" unless p
      params = if params
                 Atlas.stringify params
               else
                 Hash.new
               end
      uri    = com.ning.atlas.spi.Uri.valueOf2(p, params)

      inits = Atlas.parse_install_list(args[:init])
      @bases[name] = com.ning.atlas.Base.new(uri, inits)
    end

    def provisioner name, type, args={}
      attr                = Atlas.stringify args
      pair                = org.apache.commons.lang3.tuple.Pair.of(type.java_class , attr)
      @provisioners[name] = pair
    end

    def installer name, type, args = {}
      attr              = Atlas.stringify args
      pair              = org.apache.commons.lang3.tuple.Pair.of(type, attr)
      @installers[name] = pair
    end

    def system *args
      #no-op
    end

    def set args
      @properties.update(Atlas.stringify(args))
    end
  end


  #
  # System Parsing Magic
  #

  class RootSystemParser

    def initialize name, path
      @name, @path = name, path
      @children    = []
      @template    = open(path).read
    end

    def __parse
      eval @template, binding, @path, 1
      root = com.ning.atlas.SystemTemplate.new @name,
                                               {},
                                               [0],
                                               @children

      if root.type == "__ROOT__" and root.children.size == 1
        root.children[0]
      else
        root
      end
    end

    def server name, args={}, &block
      @children << ServerParser.new(name, args, block).__parse
    end

    def system name="system", args={}, &block
      st = if args[:external] then
             Atlas.parse_system(args[:external], name)
           else
             SystemParser.new(name, args, block).__parse
           end
      @children << st
    end


    def environment *args
      # system templates will ignore environment definitions so that you can put them
      # in the same file if you so choose
    end
  end

  class SystemParser
    java_import 'com.ning.atlas.spi.Uri'

    def initialize name, args, block
      @name, @args, @block = name, args, block
      @children            = []
    end

    def __parse
      instance_eval &@block
      # cardinality can be nil, a number, or an array
      cardinality = case it = @args[:cardinality]
                      when Array
                        it
                      when Integer
                        it.downto(1).map { |i| i - 1 }.reverse
                      else
                        ["0"]
                    end
      com.ning.atlas.SystemTemplate.new @name,
                                        @args.inject({}) { |a, (k, v)| a[k.to_s] = v; a },
                                        cardinality,
                                        @children
    end

    def system name="system", args={}, &block
      if args[:external]
      else
        @children << SystemParser.new(name, args, block).__parse
      end
    end

    def server name, args={}
      installers  = Atlas.parse_install_list(args[:install])

      # cardinality can be nil, a number, or an array
      cardinality = case it = args[:cardinality]
                      when Array
                        it
                      when Integer
                        it.downto(1).map { |i| i - 1 }.reverse
                      else
                        ["0"]
                    end

      @children << com.ning.atlas.ServerTemplate.new(name,
                                                     args[:base].to_s,
                                                     cardinality,
                                                     installers,
                                                     args.inject({}) { |a, (k, v)| a[k.to_s] = v; a })
    end
  end

end
