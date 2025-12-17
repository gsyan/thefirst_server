import re
import os
import subprocess
import platform

def open_file_location(file_path):
    """생성된 파일이 있는 폴더를 운영체제의 기본 파일 탐색기로 열기"""
    try:
        # 절대 경로로 변환
        abs_path = os.path.abspath(file_path)
        folder_path = os.path.dirname(abs_path)

        system = platform.system()
        if system == "Windows":
            # 윈도우: 파일을 선택한 상태로 탐색기 열기
            subprocess.run(f'explorer /select,"{abs_path}"', shell=True)
        elif system == "Darwin":  # macOS
            subprocess.run(['open', '-R', abs_path], check=True)
        elif system == "Linux":
            subprocess.run(['xdg-open', folder_path], check=True)
        else:
            print(f"Unsupported operating system: {system}")
            return

        print(f"Opened file location: {folder_path}")
    except Exception as e:
        print(f"Failed to open file location: {e}")
        print(f"File location: {os.path.abspath(file_path)}")

def generate_csharp_enum(java_file_path, output_file_path):
    # Java 파일 읽기
    with open(java_file_path, 'r', encoding='utf-8') as file:
        content = file.read()

    # enum 상수 파싱 (예: SUCCESS(0, "Success"))
    pattern = r'(\w+)\((\d+|Integer\.MAX_VALUE),\s*"([^"]+)"\)'  # Integer.MAX_VALUE 처리
    matches = re.findall(pattern, content)

    # C# enum 코드 생성
    csharp_code = "using System.Collections.Generic;\n\n public enum ServerErrorCode\n{\n"
    mapping_code = "public static class ErrorCodeMapping\n{\n    public static readonly Dictionary<ServerErrorCode, string> Messages = new Dictionary<ServerErrorCode, string>\n    {\n"
    for name, value, message in matches:
        # Integer.MAX_VALUE 처리
        if value == "Integer.MAX_VALUE":
            csharp_value = "int.MaxValue"
        else:
            csharp_value = value
        csharp_code += f"    {name} = {csharp_value},\n"
        mapping_code += f"        {{ ServerErrorCode.{name}, \"{message}\" }},\n"
    csharp_code += "}"
    mapping_code += "    };\n}"

    # C# 파일 쓰기
    with open(output_file_path, 'w', encoding='utf-8') as file:
        file.write(csharp_code + "\n\n" + mapping_code)

    print(f"C# enum file generated at: {output_file_path}")

    # 윈도우 탐색기에서 생성된 파일이 있는 폴더 열기
    open_file_location(output_file_path)

if __name__ == "__main__":
    java_file_path = "../../src/main/java/com/bk/sbs/exception/ServerErrorCode.java"
    output_file_path = "../../../thefirst_client_unity/Assets/Scripts/System/Util/ServerErrorCode.cs"
    generate_csharp_enum(java_file_path, output_file_path)