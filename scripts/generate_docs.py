import os
import re
import yaml

from argparse import ArgumentParser
from tabulate import tabulate
from html2text import html2text as md
from pathlib import Path

METHOD_DEF_REGEX = re.compile(
    r'^(public|def) (?P<method_name>.+?)\((?P<method_args>.+?)\) \{$')
METHOD_END_REGEX = re.compile(r'^\}$')
SCRIPT_PATH = Path()


class Arg:
    def __init__(self, a_type, name, default=None):
        self.type = a_type
        self.name = name
        self.default = default

    def __repr__(self) -> str:
        return f"Arg({self.type}: {self.name}|{self.default})"

    def __str__(self) -> str:
        return f"{self.type} {self.name}"

    @staticmethod
    def parse(data):
        args = []
        for arg in [x.strip() for x in data.split(',')]:
            arg_type = 'Object'
            arg_default = None
            if "=" in arg:
                arg_def = [a for a in arg.split('=')]
                arg_def = arg_def[0].split() + [a.strip("'").strip('"')
                                                for a in arg_def[1:]]
            else:
                arg_def = [a for a in arg.split()]
            if len(arg_def) > 2:  # specifies type/name/default
                arg_type = arg_def[0]
                arg_name = arg_def[1]
                arg_default = arg_def[2]
            elif len(arg_def) == 2:
                if "=" in arg:  # specifies name and default but no type
                    arg_name = arg_def[0]
                    arg_default = arg_def[1]
                else:  # specifies type and name but no default
                    arg_type = arg_def[0]
                    arg_name = arg_def[1]
            else:
                arg_name = arg_def[0]

            args.append(Arg(arg_type, arg_name, arg_default))
        return args


class Method:
    def __init__(self, method_name, method_body, method_args, doc):
        self.name = method_name
        self.args = Arg.parse(method_args)
        self.body = method_body
        self.doc = doc

    def __repr__(self) -> str:
        return f"Method({self.name}, {self.args})"

    def __str__(self) -> str:
        return f"{self.name}({', '.join([x.type for x in self.args])})"


def parse_file(file_lines):
    functions = []

    function_groups = {}

    function_start_line = 0

    for i, f in enumerate(file_lines):
        if f == '\n':
            continue
        line_is_method_def = METHOD_DEF_REGEX.match(f)
        if function_start_line != 0:
            if METHOD_END_REGEX.match(f):
                function_end_line = i
                function_doc_lines = []
                if file_lines[function_start_line - 1].strip() == '*/':
                    doc_end_line = function_start_line - 1
                    for doc_i, line in enumerate(file_lines[doc_end_line::-1]):
                        if line.strip() == '/*':
                            doc_start_line = doc_end_line - doc_i
                            function_doc_lines = file_lines[doc_start_line +
                                                            1:doc_end_line]
                            break
                functions.append(
                    Method(
                        method_name=function_groups.get('method_name'),
                        method_args=function_groups.get('method_args'),
                        method_body=file_lines[function_start_line +
                                               1:function_end_line - 1],
                        doc='\n'.join(
                            [x for x in function_doc_lines if x != '\n'])
                    )
                )
                function_start_line = 0
        else:
            if line_is_method_def:
                function_start_line = i
                function_groups = line_is_method_def.groupdict()

    return functions


def parse_yaml(text):
    return yaml.load(text)


def create_markdown_table(values):
    rows = []
    columns = ['Type', 'Name', 'Default']

    for row in values:
        d = [row.type, row.name]
        if row.default:
            d.append(row.default)
        else:
            d.append('')
        rows.append(d)

    return tabulate(rows, [x.title() for x in columns], tablefmt="pipe")


def represent_none(self, _):
    return self.represent_scalar('tag:yaml.org,2002:null', '')


def to_yaml(contents):
    yaml.add_representer(type(None), represent_none)
    return yaml.dump(contents, default_flow_style=False)


def update_mkdocs_yaml(groovy_files, step_files, mkdocs_file):
    print('Updating the mkdocs.yml...')
    class_links = [{x[:x.rfind('.')]: f"{x[:x.rfind('.')].upper()}.md"} for x in
                   sorted(groovy_files) if 'Constants' not in x]
    step_links = [{x[:x.rfind('.')]: f"steps/{x[:x.rfind('.')]}.md"} for x in
                  sorted(step_files)]

    with mkdocs_file.open('r') as r:
        current_yaml = parse_yaml(r.read())
        for i, x in enumerate(current_yaml['pages']):
            page_key = list(x.keys())[0]
            if page_key == 'Classes':
                current_yaml['pages'][i] = {'Classes': class_links}
            elif page_key == 'Steps':
                current_yaml['pages'][i] = {'Steps': step_links}
    with mkdocs_file.open('w') as w:
        w.write(to_yaml(current_yaml))


def create_markdown_doc(name, docs_folder, functions):
    docs_folder.mkdir(exist_ok=True)
    lines = [f"# com.concur.{name.replace('.groovy', '')}"]
    for g_function in functions:
        if g_function.name == 'getStageName':
            continue
        lines.append(f"\n## {str(g_function)}")
        if g_function.doc:
            function_yaml_def = parse_yaml(g_function.doc)
            lines.append(f"\n> {function_yaml_def.get('description')}")
            if function_yaml_def.get('note'):
                lines.append(f"\n__**{function_yaml_def.get('note')}**__")
            lines.append(f"\n{create_markdown_table(g_function.args)}")
            examples = function_yaml_def.get('examples')
            if examples:
                for i, example in enumerate(examples):
                    lines.append(f"\n### Example {i+1}")
                    lines.append(f"\n```groovy\n{example.strip()}\n```")
            example = function_yaml_def.get('example')
            if example:
                lines.append("\n### Example")
                lines.append(
                    f"\n```groovy\n{function_yaml_def.get('example').strip()}\n```")
            resources = function_yaml_def.get('resources')
            if resources:
                lines.append("\n### Resources")
                lines.append(
                    '\n'.join([f"\n* [{x.get('name')}]({x.get('url')})" for x in resources]))

    with (docs_folder / name.upper().replace('.GROOVY', '.md')).open(mode='w') as w:
        w.write('\n'.join(lines))


def create_var_markdown(f, docs_folder):
    docs_folder.mkdir(exist_ok=True)
    with f.open() as fopen:
        html_contents = fopen.read().splitlines()
    with (docs_folder / f.name.replace('.txt', '.md')).open(mode='w') as w:
        w.write(md('\n'.join(html_contents)))


def entry_point():
    parser = ArgumentParser()
    parser.add_argument('-o', '--out-path', default='')

    args = parser.parse_args()

    if args.out_path is None:
        parser.print_usage()
        exit(1)
    groovy_files = (SCRIPT_PATH / 'src' / 'com' / 'concur').glob('*.groovy')
    for fil in groovy_files:
        if 'Constants' in fil.name:
            continue
        with fil.open() as groovy_file:
            lines = groovy_file.read().splitlines()
            functions = parse_file(lines)
            if not functions:
                continue
            print(f"Generating documentation for {fil.name}...")
            create_markdown_doc(name=fil.name,
                                docs_folder=SCRIPT_PATH / args.out_path,
                                functions=functions)
    var_files = (SCRIPT_PATH / 'vars').glob("*.txt")
    for var in var_files:
        print(f"Generating documentation for {var.name}...")
        create_var_markdown(var, SCRIPT_PATH / args.out_path / 'steps')

    mkdocs_file = list(SCRIPT_PATH.glob('mkdocs.yml'))[0]
    update_mkdocs_yaml([x.name for x in (SCRIPT_PATH / 'src' / 'com' / 'concur').
                        glob('*.groovy') if x.name != 'example.groovy'],
                       [x.name for x in (
                           SCRIPT_PATH / 'vars').glob('*.txt')], mkdocs_file)


if __name__ == '__main__':
    entry_point()
